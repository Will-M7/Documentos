package pe.estudiodocumental.solicitudes.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.comun.api.dto.Pagina;
import pe.estudiodocumental.solicitudes.aplicacion.SolicitudService;
import pe.estudiodocumental.solicitudes.aplicacion.SolicitudService.Datos;
import pe.estudiodocumental.solicitudes.dominio.EstadoSolicitud;
import pe.estudiodocumental.solicitudes.dominio.Solicitud;

/** Bandeja de solicitudes del portal, para la oficina. */
@Tag(name = "Solicitudes")
@RestController
@RequestMapping("/api/v1/solicitudes")
class SolicitudController {

    private final SolicitudService solicitudes;

    SolicitudController(SolicitudService solicitudes) {
        this.solicitudes = solicitudes;
    }

    record SolicitudResumen(
            UUID id,
            String codigo,
            UUID plantillaId,
            String plantillaNombre,
            EstadoSolicitud estado,
            String contactoNombre,
            String contactoTelefono,
            String contactoCorreo,
            String motivoRechazo,
            UUID documentoId,
            Instant creadoEn,
            Instant atendidaEn) {

        static SolicitudResumen desde(Solicitud s) {
            return new SolicitudResumen(
                    s.getId(),
                    s.getCodigo(),
                    s.getPlantillaId(),
                    s.getPlantillaNombre(),
                    s.getEstado(),
                    s.getContactoNombre(),
                    s.getContactoTelefono(),
                    s.getContactoCorreo(),
                    s.getMotivoRechazo(),
                    s.getDocumentoId(),
                    s.getCreadoEn(),
                    s.getAtendidaEn());
        }
    }

    record SolicitudDetalle(SolicitudResumen resumen, Datos datos) {}

    record RechazoRequest(@Size(max = 300) String motivo) {}

    record Contador(long pendientes) {}

    @GetMapping
    Pagina<SolicitudResumen> listar(
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(solicitudes.listar(estado, pagina, tamano), SolicitudResumen::desde);
    }

    @Operation(summary = "Cantidad de solicitudes pendientes", description = "Para el aviso del menú.")
    @GetMapping("/pendientes")
    Contador pendientes() {
        return new Contador(solicitudes.pendientes());
    }

    @GetMapping("/{id}")
    SolicitudDetalle obtener(@PathVariable UUID id) {
        Solicitud s = solicitudes.obtener(id);
        return new SolicitudDetalle(SolicitudResumen.desde(s), solicitudes.datos(s));
    }

    @Operation(summary = "Rechazar", description = "El cliente verá el motivo al consultar su código.")
    @PostMapping("/{id}/rechazar")
    SolicitudResumen rechazar(
            @PathVariable UUID id, @Valid @RequestBody RechazoRequest s, @AuthenticationPrincipal Jwt token) {
        return SolicitudResumen.desde(solicitudes.rechazar(id, s.motivo(), UUID.fromString(token.getSubject())));
    }
}
