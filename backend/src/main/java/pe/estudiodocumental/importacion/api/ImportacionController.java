package pe.estudiodocumental.importacion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.estudiodocumental.importacion.aplicacion.ImportacionV1Service;
import pe.estudiodocumental.importacion.aplicacion.ImportacionV1Service.Resultado;

@Tag(name = "Importación")
@RestController
@RequestMapping("/api/v1/importacion")
class ImportacionController {

    private final ImportacionV1Service importacion;

    ImportacionController(ImportacionV1Service importacion) {
        this.importacion = importacion;
    }

    @Operation(
            summary = "Importar desde la v1",
            description = "ZIP exportado por la aplicación de escritorio (clients.json y/o configuration.json). "
                    + "Se puede repetir: no duplica clientes ni plantillas. Solo ADMIN.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(path = "/v1", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Resultado importarV1(@RequestPart("archivo") MultipartFile archivo) throws IOException {
        return importacion.importar(archivo.getBytes());
    }
}
