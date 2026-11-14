package pe.estudiodocumental.plantillas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.EditarPlantillaRequest;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.ParticipanteDto;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.PlantillaDetalle;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.PlantillaResumen;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.VariableDto;
import pe.estudiodocumental.plantillas.api.dto.PlantillaDtos.VersionDto;
import pe.estudiodocumental.plantillas.aplicacion.PlantillaService;
import pe.estudiodocumental.plantillas.aplicacion.PlantillaService.ConfiguracionParticipante;
import pe.estudiodocumental.plantillas.dominio.Plantilla;

@Tag(name = "Plantillas")
@RestController
@RequestMapping("/api/v1/plantillas")
class PlantillaController {

    private final PlantillaService plantillas;

    PlantillaController(PlantillaService plantillas) {
        this.plantillas = plantillas;
    }

    @Operation(summary = "Listar plantillas", description = "Por defecto solo las activas.")
    @GetMapping
    List<PlantillaResumen> listar(@RequestParam(defaultValue = "true") boolean soloActivas) {
        return plantillas.listar(soloActivas).stream()
                .map(PlantillaResumen::desde)
                .toList();
    }

    @GetMapping("/{id}")
    PlantillaDetalle obtener(@PathVariable UUID id) {
        return detalle(plantillas.obtener(id));
    }

    @Operation(summary = "Subir plantilla", description = "DOCX con marcas {{...}}. Solo ADMIN.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PlantillaDetalle crear(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam(required = false) String categoria)
            throws IOException {
        return detalle(
                plantillas.crear(nombre, descripcion, categoria, archivo.getOriginalFilename(), archivo.getBytes()));
    }

    @Operation(
            summary = "Subir nueva versión",
            description = "Reemplaza el DOCX; las versiones anteriores se conservan.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/{id}/versiones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PlantillaDetalle nuevaVersion(@PathVariable UUID id, @RequestPart("archivo") MultipartFile archivo)
            throws IOException {
        return detalle(plantillas.subirVersion(id, archivo.getOriginalFilename(), archivo.getBytes()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    PlantillaDetalle editar(@PathVariable UUID id, @Valid @RequestBody EditarPlantillaRequest s) {
        List<ConfiguracionParticipante> config = s.participantes().stream()
                .map(p -> new ConfiguracionParticipante(p.clave(), p.etiqueta(), p.tipoCliente()))
                .toList();
        return detalle(plantillas.editar(id, s.nombre(), s.descripcion(), s.categoria(), s.activo(), config));
    }

    @Operation(summary = "Descargar el DOCX de una versión")
    @GetMapping("/{id}/versiones/{numero}/docx")
    ResponseEntity<byte[]> descargar(@PathVariable UUID id, @PathVariable int numero) {
        var contenido = plantillas.archivoDeVersion(id, numero);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenido.tipoMime()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(contenido.nombre(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(contenido.bytes());
    }

    private PlantillaDetalle detalle(Plantilla p) {
        return new PlantillaDetalle(
                p.getId(),
                p.getNombre(),
                p.getDescripcion(),
                p.getCategoria(),
                p.getVersionActual(),
                p.isActivo(),
                p.getParticipantes().stream().map(ParticipanteDto::desde).toList(),
                plantillas.variablesDe(p).stream().map(VariableDto::desde).toList(),
                plantillas.versiones(p.getId()).stream().map(VersionDto::desde).toList());
    }
}
