package pe.estudiodocumental.consultas.aplicacion;

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

    public ConsultaService(
            ClienteService clientes,
            CredencialRepository credenciales,
            ConsultaExternaRepository registro,
            CifradoTokens cifrado,
            List<ProveedorConsulta> proveedores,
            ApplicationEventPublisher eventos) {
        this.clientes = clientes;
        this.credenciales = credenciales;
        this.registro = registro;
        this.cifrado = cifrado;
        this.eventos = eventos;
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
        List<CredencialApi> activas = credenciales.findByActivoTrueOrderByPrioridadAscCreadoEnAsc();
        if (activas.isEmpty()) {
            return new Resultado(
                    null,
                    null,
                    "No está registrado y no hay credenciales de consulta configuradas. Complete los datos a mano.");
        }
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
                registro.save(new ConsultaExterna(
                        tipo.name(), normalizado, credencial.getProveedor(), true, null, usuarioId));
                return new Resultado(
                        credencial.getProveedor().nombre(),
                        cliente,
                        "Datos obtenidos de %s y guardados en Clientes."
                                .formatted(credencial.getProveedor().nombre()));
            } catch (FalloConsulta | ReglaNegocioException | IllegalStateException e) {
                ultimoError = e.getMessage();
                credencial.registrarUso(e.getMessage());
                registro.save(new ConsultaExterna(
                        tipo.name(), normalizado, credencial.getProveedor(), false, e.getMessage(), usuarioId));
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
        return credencial.getProveedor() == Proveedor.FACTILIZA ? OrigenDatos.FACTILIZA : OrigenDatos.APISPERU;
    }
}
