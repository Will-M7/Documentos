package pe.estudiodocumental.documentos.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.archivos.aplicacion.ArchivoService;
import pe.estudiodocumental.comun.api.dto.Pagina;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.documentos.api.dto.DocumentoDtos.DocumentoDetalle;
import pe.estudiodocumental.documentos.api.dto.DocumentoDtos.DocumentoResumen;
import pe.estudiodocumental.documentos.api.dto.DocumentoDtos.GenerarRequest;
import pe.estudiodocumental.documentos.api.dto.DocumentoDtos.ParticipanteDto;
import pe.estudiodocumental.documentos.aplicacion.DocumentoService;
import pe.estudiodocumental.documentos.aplicacion.DocumentoService.DatosParticipante;
import pe.estudiodocumental.documentos.dominio.Documento;
import pe.estudiodocumental.identidad.aplicacion.UsuarioConsultaService;

@Tag(name = "Documentos")
@RestController
@RequestMapping("/api/v1/documentos")
class DocumentoController {

    private final DocumentoService documentos;
    private final UsuarioConsultaService usuarios;

    DocumentoController(DocumentoService documentos, UsuarioConsultaService usuarios) {
        this.documentos = documentos;
        this.usuarios = usuarios;
    }

    @Operation(
            summary = "Generar documento",
            description =
                    "Guarda o completa los clientes, rellena la plantilla y genera DOCX (y PDF si hay LibreOffice).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DocumentoDetalle generar(@Valid @RequestBody GenerarRequest s, @AuthenticationPrincipal Jwt token) {
        List<DatosParticipante> participantes = new ArrayList<>();
        if (s.participantes() != null) {
            s.participantes().forEach((clave, datos) -> {
                try {
                    participantes.add(new DatosParticipante(clave, datos.aDatos()));
                } catch (ReglaNegocioException e) {
                    throw new ReglaNegocioException("«%s»: %s".formatted(clave, e.getMessage()));
                }
            });
        }
        Documento documento = documentos.generar(
                s.plantillaId(), s.titulo(), s.variables(), participantes, UUID.fromString(token.getSubject()));
        return detalle(documento);
    }

    @Operation(summary = "Historial", description = "Busca por título, plantilla, nombre o documento de identidad.")
    @GetMapping
    Pagina<DocumentoResumen> buscar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        var resultado = documentos.buscar(q, desde, hasta, pagina, tamano);
        Set<UUID> ids = new HashSet<>();
        resultado.forEach(d -> ids.add(d.getUsuarioId()));
        Map<UUID, String> nombres = usuarios.nombres(ids);
        return Pagina.de(resultado, d -> resumen(d, nombres));
    }

    @GetMapping("/{id}")
    DocumentoDetalle obtener(@PathVariable UUID id) {
        return detalle(documentos.obtener(id));
    }

    @GetMapping("/{id}/docx")
    ResponseEntity<byte[]> docx(@PathVariable UUID id) {
        return archivo(documentos.archivo(id, false), false);
    }

    @Operation(
            summary = "PDF",
            description = "Con ?verEnLinea=true se muestra en el navegador en lugar de descargarse.")
    @GetMapping("/{id}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean verEnLinea) {
        return archivo(documentos.archivo(id, true), verEnLinea);
    }

    private DocumentoDetalle detalle(Documento d) {
        var valores = documentos.valores(d);
        return new DocumentoDetalle(
                resumen(d, usuarios.nombres(Set.of(d.getUsuarioId()))),
                valores.getOrDefault("variables", Map.of()),
                valores.getOrDefault("marcas", Map.of()),
                d.getPdfArchivoId() == null ? documentos.avisoPdf() : null);
    }

    private static DocumentoResumen resumen(Documento d, Map<UUID, String> nombres) {
        return new DocumentoResumen(
                d.getId(),
                d.getNumero(),
                d.getTitulo(),
                d.getPlantillaId(),
                d.getPlantillaNombre(),
                nombres.getOrDefault(d.getUsuarioId(), "—"),
                d.getCreadoEn(),
                d.getPdfArchivoId() != null,
                d.getParticipantes().stream()
                        .map(p -> new ParticipanteDto(
                                p.getParticipante(),
                                p.getEtiqueta(),
                                p.getClienteId(),
                                p.getNombre(),
                                p.getDocumentoIdentidad()))
                        .toList());
    }

    private static ResponseEntity<byte[]> archivo(ArchivoService.Contenido contenido, boolean enLinea) {
        ContentDisposition disposicion = (enLinea ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(contenido.nombre(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenido.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(contenido.bytes());
    }
}
