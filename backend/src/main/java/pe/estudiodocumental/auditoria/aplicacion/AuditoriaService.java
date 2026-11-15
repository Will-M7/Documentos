package pe.estudiodocumental.auditoria.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.auditoria.dominio.RegistroAuditoria;
import pe.estudiodocumental.auditoria.infraestructura.AuditoriaRepository;
import pe.estudiodocumental.comun.eventos.EventoAuditoria;
import pe.estudiodocumental.identidad.aplicacion.UsuarioConsultaService;

/** Guarda los eventos de auditoría que publican los demás módulos y permite consultarlos. */
@Service
public class AuditoriaService {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final AuditoriaRepository registros;
    private final UsuarioConsultaService usuarios;

    public AuditoriaService(AuditoriaRepository registros, UsuarioConsultaService usuarios) {
        this.registros = registros;
        this.usuarios = usuarios;
    }

    /**
     * Se ejecuta dentro de la misma transacción de quien publica: si la operación se deshace, el registro también
     * (los ingresos fallidos se conservan porque el login no deshace su transacción al fallar).
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRED)
    public void registrar(EventoAuditoria evento) {
        UUID usuarioId = evento.usuarioId() != null ? evento.usuarioId() : usuarioDeLaSesion();
        String nombre =
                usuarioId == null ? null : usuarios.nombres(Set.of(usuarioId)).getOrDefault(usuarioId, null);
        registros.save(new RegistroAuditoria(
                usuarioId, nombre, evento.accion(), evento.entidad(), evento.entidadId(), evento.detalle()));
    }

    @Transactional(readOnly = true)
    public Page<RegistroAuditoria> buscar(String texto, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        Instant inicio = (desde == null ? LocalDate.of(2000, 1, 1) : desde)
                .atStartOfDay(ZONA)
                .toInstant();
        Instant fin = (hasta == null ? LocalDate.now(ZONA) : hasta)
                .plusDays(1)
                .atStartOfDay(ZONA)
                .toInstant();
        return registros.buscar(
                texto == null ? "" : texto.strip(),
                inicio,
                fin,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.clamp(tamano, 1, 200),
                        Sort.by("creadoEn").descending()));
    }

    private static UUID usuarioDeLaSesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }

    /** Nombres legibles de las acciones, para la pantalla. */
    public static final Map<String, String> ACCIONES = Map.ofEntries(
            Map.entry("INGRESO", "Ingresó al sistema"),
            Map.entry("INGRESO_FALLIDO", "Contraseña incorrecta"),
            Map.entry("USUARIO_BLOQUEADO", "Usuario bloqueado"),
            Map.entry("CLAVE_CAMBIADA", "Cambió su contraseña"),
            Map.entry("CLAVE_RESTABLECIDA", "Restableció una contraseña"),
            Map.entry("USUARIO_CREADO", "Creó un usuario"),
            Map.entry("USUARIO_EDITADO", "Editó un usuario"),
            Map.entry("CLIENTE_CREADO", "Registró un cliente"),
            Map.entry("CLIENTE_EDITADO", "Editó un cliente"),
            Map.entry("CLIENTE_BAJA", "Dio de baja un cliente"),
            Map.entry("CONSULTA_EXTERNA", "Consultó DNI/RUC a un proveedor"),
            Map.entry("PLANTILLA_SUBIDA", "Subió una plantilla"),
            Map.entry("PLANTILLA_VERSION", "Subió una versión de plantilla"),
            Map.entry("PLANTILLA_EDITADA", "Editó una plantilla"),
            Map.entry("DOCUMENTO_GENERADO", "Generó un documento"),
            Map.entry("DOCUMENTO_DESCARGADO", "Descargó un documento"),
            Map.entry("CREDENCIAL_GUARDADA", "Guardó una credencial de API"),
            Map.entry("CREDENCIAL_ELIMINADA", "Eliminó una credencial de API"));
}
