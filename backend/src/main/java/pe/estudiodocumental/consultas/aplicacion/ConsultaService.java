package pe.estudiodocumental.consultas.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.clientes.aplicacion.ClienteService;
import pe.estudiodocumental.clientes.dominio.Cliente;
import pe.estudiodocumental.clientes.dominio.DatosCliente;
import pe.estudiodocumental.clientes.dominio.OrigenDatos;
import pe.estudiodocumental.clientes.dominio.TipoCliente;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.eventos.EventoAuditoria;
import pe.estudiodocumental.consultas.dominio.ConsultaExterna;
import pe.estudiodocumental.consultas.dominio.CredencialApi;
import pe.estudiodocumental.consultas.dominio.Proveedor;
import pe.estudiodocumental.consultas.infraestructura.CifradoTokens;
import pe.estudiodocumental.consultas.infraestructura.ConsultaExternaRepository;
import pe.estudiodocumental.consultas.infraestructura.CredencialRepository;
import pe.estudiodocumental.consultas.infraestructura.ProveedorConsulta;
import pe.estudiodocumental.consultas.infraestructura.ProveedorConsulta.DatosConsultados;
import pe.estudiodocumental.consultas.infraestructura.ProveedorConsulta.FalloConsulta;
import pe.estudiodocumental.consultas.infraestructura.ProveedorConsulta.Motivo;
import pe.estudiodocumental.documentos.aplicacion.DocumentoService;

/**
 * Consulta de DNI/RUC: primero en Clientes (gratis y sin internet); si no está, en los proveedores configurados por
 * orden de prioridad, pasando al siguiente si uno falla. Todo resultado externo se guarda en Clientes.
 */
@Service
public class ConsultaService {

    private final ClienteService clientes;
    private final CredencialRepository credenciales;
    private final ConsultaExternaRepository registro;
    private final CifradoTokens cifrado;
    private final Map<Proveedor, ProveedorConsulta> proveedores;

    private final ApplicationEventPublisher eventos;
    private final LimiteConsultas limite;

    public ConsultaService(
            ClienteService clientes,
            CredencialRepository credenciales,
            ConsultaExternaRepository registro,
            CifradoTokens cifrado,
            List<ProveedorConsulta> proveedores,
            ApplicationEventPublisher eventos,
            LimiteConsultas limite) {
        this.clientes = clientes;
        this.credenciales = credenciales;
        this.registro = registro;
        this.cifrado = cifrado;
        this.eventos = eventos;
        this.limite = limite;
        this.proveedores =
                proveedores.stream().collect(Collectors.toMap(ProveedorConsulta::proveedor, Function.identity()));
    }

    /**
     * @param origen "CLIENTES" si salió de la base local, o el nombre del proveedor.
     * @param cliente el cliente encontrado, o null.
     * @param mensaje explicación para mostrar al usuario.
     */
    public record Resultado(String origen, Cliente cliente, String mensaje) {}

    /**
     * @param actualizar true para ignorar Clientes y pedir los datos de nuevo al proveedor.
     */
    @Transactional
    public Resultado consultar(TipoDocumento tipo, String numero, boolean actualizar, UUID usuarioId) {
        if (tipo != TipoDocumento.DNI && tipo != TipoDocumento.RUC) {
            throw new ReglaNegocioException("Solo se pueden consultar DNI y RUC.");
        }
        String normalizado = TipoDocumento.normalizar(numero);
        if (!tipo.esValido(normalizado)) {
            throw new ReglaNegocioException(tipo.mensajeFormato());
        }
        if (!actualizar) {
            Optional<Cliente> local = clientes.buscarPorDocumento(tipo, normalizado);
            if (local.isPresent()) {
                return new Resultado("CLIENTES", local.get(), "Datos cargados desde Clientes (sin costo).");
            }
        }
        Instant ahora = Instant.now();
        List<CredencialApi> configuradas = credenciales.findByActivoTrueOrderByPrioridadAscCreadoEnAsc();
        // Se saltan las que ya gastaron su cupo del mes: se usa la siguiente automáticamente
        List<CredencialApi> activas =
                configuradas.stream().filter(c -> conCupo(c, ahora)).toList();
        if (configuradas.isEmpty()) {
            return new Resultado(
                    null,
                    null,
                    "No está registrado y no hay credenciales de consulta configuradas. Complete los datos a mano.");
        }
        if (activas.isEmpty()) {
            return new Resultado(
                    null,
                    null,
                    "Todas las credenciales gastaron su cupo gratis de este mes. Complete los datos a mano o agregue otra"
                            + " credencial en Configuración.");
        }
        limite.registrar(usuarioId);
        String ultimoError = Motivo.NO_ENCONTRADO.mensaje();
        for (CredencialApi credencial : activas) {
            ProveedorConsulta proveedor = proveedores.get(credencial.getProveedor());
            try {
                DatosConsultados datos =
                        proveedor.consultar(tipo.name(), normalizado, cifrado.descifrar(credencial.getTokenCifrado()));
                Cliente cliente = clientes.guardarOCompletar(aDatosCliente(tipo, datos), origen(credencial));
                credencial.registrarUso("Correcta");
                eventos.publishEvent(EventoAuditoria.de(
                        "CONSULTA_EXTERNA",
                        "cliente",
                        cliente.getId(),
                        "%s %s vía %s"
                                .formatted(
                                        tipo,
                                        normalizado,
                                        credencial.getProveedor().nombre())));
                registro.save(new ConsultaExterna(tipo.name(), normalizado, credencial, true, null, usuarioId));
                return new Resultado(
                        credencial.getProveedor().nombre(),
                        cliente,
                        "Datos obtenidos de %s y guardados en Clientes."
                                .formatted(credencial.getProveedor().nombre()));
            } catch (FalloConsulta | ReglaNegocioException | IllegalStateException e) {
                ultimoError = e.getMessage();
                credencial.registrarUso(e.getMessage());
                registro.save(
                        new ConsultaExterna(tipo.name(), normalizado, credencial, false, e.getMessage(), usuarioId));
                if (e instanceof FalloConsulta f && f.motivo() == Motivo.LIMITE) {
                    // El proveedor dice que se acabó el cupo: descansa hasta el próximo mes y se sigue con la siguiente
                    credencial.marcarAgotada(inicioDelProximoMes());
                }
                if (e instanceof FalloConsulta f && f.motivo() == Motivo.NO_ENCONTRADO) {
                    break;
                }
            }
        }
        return new Resultado(null, null, ultimoError + " Puede completar los datos a mano.");
    }

    private static DatosCliente aDatosCliente(TipoDocumento tipo, DatosConsultados d) {
        return tipo == TipoDocumento.DNI
                ? new DatosCliente(
                        TipoCliente.PERSONA,
                        TipoDocumento.DNI,
                        d.numero(),
                        d.nombres(),
                        d.apellidoPaterno(),
                        d.apellidoMaterno(),
                        null,
                        d.direccion(),
                        null,
                        null)
                : new DatosCliente(
                        TipoCliente.EMPRESA,
                        TipoDocumento.RUC,
                        d.numero(),
                        null,
                        null,
                        null,
                        d.razonSocial(),
                        d.direccion(),
                        null,
                        null);
    }

    private static OrigenDatos origen(CredencialApi credencial) {
        return switch (credencial.getProveedor()) {
            case FACTILIZA -> OrigenDatos.FACTILIZA;
            case APISPERU -> OrigenDatos.APISPERU;
            case DECOLECTA -> OrigenDatos.DECOLECTA;
        };
    }

    /** Tiene cupo si no está marcada como agotada y no llegó a su límite mensual. */
    private boolean conCupo(CredencialApi c, Instant ahora) {
        if (c.estaAgotada(ahora)) {
            return false;
        }
        return c.getLimiteMensual() == null
                || registro.contarDeCredencialDesde(c.getId(), inicioDelMes()) < c.getLimiteMensual();
    }

    static Instant inicioDelMes() {
        return LocalDate.now(DocumentoService.ZONA)
                .withDayOfMonth(1)
                .atStartOfDay(DocumentoService.ZONA)
                .toInstant();
    }

    private static Instant inicioDelProximoMes() {
        return LocalDate.now(DocumentoService.ZONA)
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(DocumentoService.ZONA)
                .toInstant();
    }
}
