package pe.estudiodocumental.auditoria.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.auditoria.aplicacion.AuditoriaService;
import pe.estudiodocumental.auditoria.dominio.RegistroAuditoria;
import pe.estudiodocumental.comun.api.dto.Pagina;

@Tag(name = "Auditoría")
@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasRole('ADMIN')")
class AuditoriaController {

    private final AuditoriaService auditoria;

    AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    record RegistroResponse(
            UUID id,
            Instant fecha,
            String usuario,
            String accion,
            String descripcion,
            String entidad,
            UUID entidadId,
            String detalle) {

        static RegistroResponse desde(RegistroAuditoria r) {
            return new RegistroResponse(
                    r.getId(),
                    r.getCreadoEn(),
                    r.getUsuarioNombre(),
                    r.getAccion(),
                    AuditoriaService.ACCIONES.getOrDefault(r.getAccion(), r.getAccion()),
                    r.getEntidad(),
                    r.getEntidadId(),
                    r.getDetalle());
        }
    }

    @Operation(summary = "Registro de auditoría", description = "Busca por acción, usuario o detalle. Solo ADMIN.")
    @GetMapping
    Pagina<RegistroResponse> buscar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        return Pagina.de(auditoria.buscar(q, desde, hasta, pagina, tamano), RegistroResponse::desde);
    }
}
