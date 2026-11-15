package pe.estudiodocumental.solicitudes.aplicacion;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.clientes.api.dto.ClienteRequest;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.eventos.DocumentoGenerado;
import pe.estudiodocumental.comun.eventos.EventoAuditoria;
import pe.estudiodocumental.comun.eventos.GenerandoDocumento;
import pe.estudiodocumental.documentos.aplicacion.DocumentoService;
import pe.estudiodocumental.documentos.aplicacion.DocumentoService.DatosParticipante;
import pe.estudiodocumental.plantillas.aplicacion.PlantillaService;
import pe.estudiodocumental.plantillas.dominio.ParticipantePlantilla;
import pe.estudiodocumental.plantillas.dominio.Plantilla;
import pe.estudiodocumental.solicitudes.dominio.EstadoSolicitud;
import pe.estudiodocumental.solicitudes.dominio.Solicitud;
import pe.estudiodocumental.solicitudes.infraestructura.SolicitudRepository;
import pe.estudiodocumental.variables.dominio.Variable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Solicitudes del portal de clientes. El cliente envía sus datos (se validan igual que al generar, sin guardar
 * clientes ni documentos); la oficina las revisa y, al generar el documento, la solicitud queda atendida sola.
 */
@Service
public class SolicitudService {

    /** Sin letras ni números que se confundan (0/O, 1/I/L). */
    private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private final SolicitudRepository solicitudes;
    private final PlantillaService plantillas;
    private final DocumentoService documentos;
    private final LimitePortal limite;
    private final JsonMapper json;
    private final ApplicationEventPublisher eventos;
    private final SecureRandom azar = new SecureRandom();

    public SolicitudService(
            SolicitudRepository solicitudes,
            PlantillaService plantillas,
            DocumentoService documentos,
            LimitePortal limite,
            JsonMapper json,
            ApplicationEventPublisher eventos) {
        this.solicitudes = solicitudes;
        this.plantillas = plantillas;
        this.documentos = documentos;
        this.limite = limite;
        this.json = json;
        this.eventos = eventos;
    }

    /** Lo que envió el cliente, tal cual, para mostrarlo a la oficina o rellenar el formulario. */
    public record Datos(Map<String, String> variables, Map<String, ClienteRequest> participantes) {}

    public record Contacto(String nombre, String telefono, String correo) {}

    @Transactional(readOnly = true)
    public List<Plantilla> plantillasPublicas() {
        return plantillas.publicas();
    }

    @Transactional(readOnly = true)
    public Plantilla plantillaPublica(UUID id) {
        Plantilla plantilla = plantillas.obtener(id);
        if (!plantilla.isActivo() || !plantilla.isPublica()) {
            throw new RecursoNoEncontradoException("Ese documento no está disponible en el portal.");
        }
        return plantilla;
    }

    @Transactional
    public Solicitud crear(UUID plantillaId, Datos enviados, Contacto contacto, boolean aceptaDatos, String ip) {
        // Se verifica antes, pero solo cuentan los envíos que se guardan: un error de tipeo no gasta el cupo
        limite.verificarEnvio(ip);
        Plantilla plantilla = plantillaPublica(plantillaId);
        if (!aceptaDatos) {
            throw new ReglaNegocioException("Debe autorizar el tratamiento de sus datos personales para continuar.");
        }
        Datos datos = soloDeLaPlantilla(plantilla, enviados);
        // Las mismas validaciones que al generar (campos obligatorios, formatos, DNI/RUC), sin guardar nada
        documentos.vistaPrevia(plantillaId, datos.variables(), participantes(datos));
        limite.registrarEnvio(ip);
        Solicitud solicitud = solicitudes.save(new Solicitud(
                codigoNuevo(),
                plantillaId,
                plantilla.getNombre(),
                json.writeValueAsString(datos),
                contacto.nombre(),
                contacto.telefono(),
                contacto.correo(),
                true));
        eventos.publishEvent(EventoAuditoria.de(
                "SOLICITUD_RECIBIDA",
                "solicitud",
                solicitud.getId(),
                "%s · %s · %s".formatted(solicitud.getCodigo(), plantilla.getNombre(), solicitud.getContactoNombre())));
        return solicitud;
    }

    /** Guarda solo las variables y participantes que la plantilla usa; lo demás que llegue se descarta. */
    private Datos soloDeLaPlantilla(Plantilla plantilla, Datos enviados) {
        Map<String, String> variables = new LinkedHashMap<>();
        Map<String, String> llegaron = enviados.variables() == null ? Map.of() : enviados.variables();
        for (Variable v : plantillas.variablesDe(plantilla)) {
            if (llegaron.containsKey(v.getClave())) {
                variables.put(v.getClave(), llegaron.get(v.getClave()));
            }
        }
        Map<String, ClienteRequest> participantes = new LinkedHashMap<>();
        Map<String, ClienteRequest> enviadosP = enviados.participantes() == null ? Map.of() : enviados.participantes();
        for (ParticipantePlantilla p : plantilla.getParticipantes()) {
            if (enviadosP.get(p.getClave()) != null) {
                participantes.put(p.getClave(), enviadosP.get(p.getClave()));
            }
        }
        return new Datos(variables, participantes);
    }

    /** Lo que el cliente puede ver con su código: solo el estado, sin datos personales. */
    @Transactional(readOnly = true)
    public Solicitud porCodigo(String codigo, String ip) {
        limite.registrarConsulta(ip);
        return solicitudes
                .findByCodigo(codigo == null ? "" : codigo.strip().toUpperCase())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una solicitud con ese código."));
    }

    @Transactional(readOnly = true)
    public Page<Solicitud> listar(EstadoSolicitud estado, int pagina, int tamano) {
        var orden = PageRequest.of(
                Math.max(pagina, 0),
                Math.clamp(tamano, 1, 100),
                Sort.by("creadoEn").descending());
        return estado == null ? solicitudes.findAll(orden) : solicitudes.findByEstado(estado, orden);
    }

    @Transactional(readOnly = true)
    public long pendientes() {
        return solicitudes.countByEstado(EstadoSolicitud.PENDIENTE);
    }

    @Transactional(readOnly = true)
    public Solicitud obtener(UUID id) {
        return solicitudes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe."));
    }

    public Datos datos(Solicitud solicitud) {
        return json.readValue(solicitud.getDatos(), new TypeReference<>() {});
    }

    @Transactional
    public Solicitud rechazar(UUID id, String motivo, UUID usuarioId) {
        Solicitud solicitud = paraActualizar(id);
        solicitud.rechazar(motivo, usuarioId);
        eventos.publishEvent(EventoAuditoria.de(
                "SOLICITUD_RECHAZADA", "solicitud", id, solicitud.getCodigo() + " · " + motivo.strip()));
        return solicitud;
    }

    /**
     * Antes de generar el documento de una solicitud: la bloquea (otro operador que intente atenderla o rechazarla
     * espera) y verifica que siga pendiente y sea de esa plantilla. Si no, se cancela la generación.
     */
    @EventListener
    @Transactional
    public void antesDeGenerar(GenerandoDocumento evento) {
        Solicitud solicitud = paraActualizar(evento.solicitudId());
        solicitud.verificarPendiente();
        if (!solicitud.getPlantillaId().equals(evento.plantillaId())) {
            throw new ReglaNegocioException("La solicitud %s es de otro documento («%s»)."
                    .formatted(solicitud.getCodigo(), solicitud.getPlantillaNombre()));
        }
    }

    /** Al generar el documento de una solicitud, queda atendida (en la misma transacción). */
    @EventListener
    @Transactional
    public void alGenerarDocumento(DocumentoGenerado evento) {
        if (evento.solicitudId() != null) {
            paraActualizar(evento.solicitudId()).atender(evento.documentoId(), evento.usuarioId());
        }
    }

    private Solicitud paraActualizar(UUID id) {
        return solicitudes
                .buscarParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La solicitud no existe."));
    }

    private static List<DatosParticipante> participantes(Datos datos) {
        List<DatosParticipante> lista = new ArrayList<>();
        Map<String, ClienteRequest> enviados =
                datos.participantes() == null ? new LinkedHashMap<>() : datos.participantes();
        enviados.forEach((clave, cliente) -> {
            try {
                lista.add(new DatosParticipante(clave, cliente.aDatos()));
            } catch (ReglaNegocioException e) {
                throw new ReglaNegocioException("«%s»: %s".formatted(clave, e.getMessage()));
            }
        });
        return lista;
    }

    private String codigoNuevo() {
        StringBuilder codigo = new StringBuilder("S-");
        for (int i = 0; i < 6; i++) {
            codigo.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        String resultado = codigo.toString();
        return solicitudes.findByCodigo(resultado).isPresent() ? codigoNuevo() : resultado;
    }
}
