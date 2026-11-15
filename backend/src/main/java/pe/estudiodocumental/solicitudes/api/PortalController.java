package pe.estudiodocumental.solicitudes.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.clientes.api.dto.ClienteRequest;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.ParticipanteDto;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.VariableDto;
import pe.estudiodocumental.plantillas.aplicacion.PlantillaService;
import pe.estudiodocumental.plantillas.dominio.Plantilla;
import pe.estudiodocumental.solicitudes.aplicacion.SolicitudService;
import pe.estudiodocumental.solicitudes.aplicacion.SolicitudService.Contacto;
import pe.estudiodocumental.solicitudes.aplicacion.SolicitudService.Datos;
import pe.estudiodocumental.solicitudes.dominio.EstadoSolicitud;
import pe.estudiodocumental.solicitudes.dominio.Solicitud;

/**
 * Portal de clientes: público, sin usuario. Solo muestra plantillas marcadas como públicas y, al consultar un código,
 * solo el estado (nunca datos personales).
 */
@Tag(name = "Portal de clientes (público)")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/publico")
class PortalController {

    private final SolicitudService solicitudes;
    private final PlantillaService plantillas;

    PortalController(SolicitudService solicitudes, PlantillaService plantillas) {
        this.solicitudes = solicitudes;
        this.plantillas = plantillas;
    }

    record PlantillaPublica(
            UUID id,
            String nombre,
            String descripcion,
            String categoria,
            List<ParticipanteDto> participantes,
            List<VariableDto> variables) {}

    record SolicitudRequest(
            @NotNull(message = "Elija el documento") UUID plantillaId,
            @Size(max = 100, message = "Demasiados datos") Map<String, @Size(max = 2000) String> variables,

            @Size(max = 20, message = "Demasiados participantes")
            Map<String, @Valid ClienteRequest> participantes,

            @NotBlank(message = "Escriba su nombre") @Size(max = 150)
            String contactoNombre,

            @Size(max = 30) String contactoTelefono,

            @Email(message = "Correo no válido") @Size(max = 120)
            String contactoCorreo,

            boolean aceptaDatos) {}

    record SolicitudCreada(String codigo, String mensaje) {}

    record EstadoPublico(String codigo, String documento, EstadoSolicitud estado, String mensaje, Instant enviadaEn) {}

    @Operation(summary = "Documentos que se pueden pedir")
    @GetMapping("/plantillas")
    List<PlantillaPublica> plantillas() {
        return solicitudes.plantillasPublicas().stream().map(this::publica).toList();
    }

    @GetMapping("/plantillas/{id}")
    PlantillaPublica plantilla(@PathVariable UUID id) {
        return publica(solicitudes.plantillaPublica(id));
    }

    @Operation(summary = "Enviar una solicitud", description = "Devuelve un código para consultar el estado.")
    @PostMapping("/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    SolicitudCreada enviar(@Valid @RequestBody SolicitudRequest s, HttpServletRequest pedido) {
        Solicitud solicitud = solicitudes.crear(
                s.plantillaId(),
                new Datos(s.variables(), s.participantes()),
                new Contacto(s.contactoNombre(), s.contactoTelefono(), s.contactoCorreo()),
                s.aceptaDatos(),
                pedido.getRemoteAddr());
        return new SolicitudCreada(
                solicitud.getCodigo(),
                "Recibimos su solicitud. Guarde el código %s para consultar cuándo esté listo."
                        .formatted(solicitud.getCodigo()));
    }

    @Operation(summary = "Consultar el estado con el código")
    @GetMapping("/solicitudes/{codigo}")
    EstadoPublico estado(@PathVariable String codigo, HttpServletRequest pedido) {
        Solicitud s = solicitudes.porCodigo(codigo, pedido.getRemoteAddr());
        String mensaje = switch (s.getEstado()) {
            case PENDIENTE -> "La oficina está revisando su solicitud.";
            case ATENDIDA -> "Su documento está listo. Puede acercarse a la oficina.";
            case RECHAZADA -> "No se pudo atender: " + s.getMotivoRechazo();
        };
        return new EstadoPublico(s.getCodigo(), s.getPlantillaNombre(), s.getEstado(), mensaje, s.getCreadoEn());
    }

    private PlantillaPublica publica(Plantilla p) {
        return new PlantillaPublica(
                p.getId(),
                p.getNombre(),
                p.getDescripcion(),
                p.getCategoria(),
                p.getParticipantes().stream().map(ParticipanteDto::desde).toList(),
                plantillas.variablesDe(p).stream().map(VariableDto::desde).toList());
    }
}
