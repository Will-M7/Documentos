package pe.estudiodocumental.archivos.infraestructura;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guarda los archivos en una carpeta del servidor, organizados por año y mes: {@code 2026/11/<uuid>.docx}. Esa
 * carpeta debe incluirse en los respaldos junto con la base de datos.
 */
@Component
class AlmacenEnDisco implements AlmacenArchivos {

    private final Path raiz;

    AlmacenEnDisco(@Value("${app.archivos.directorio}") String directorio) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(byte[] contenido, String extension) {
        LocalDate hoy = LocalDate.now();
        String ruta = "%d/%02d/%s.%s".formatted(hoy.getYear(), hoy.getMonthValue(), UUID.randomUUID(), extension);
        Path destino = resolver(ruta);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo en " + destino, e);
        }
        return ruta;
    }

    @Override
    public byte[] leer(String ruta) {
        try {
            return Files.readAllBytes(resolver(ruta));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo " + ruta, e);
        }
    }

    /** Impide salir de la carpeta raíz con rutas como "../../". */
    private Path resolver(String ruta) {
        Path archivo = raiz.resolve(ruta).normalize();
        if (!archivo.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta de archivo no permitida: " + ruta);
        }
        return archivo;
    }
}
