package pe.estudiodocumental.documentos.infraestructura;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

/**
 * PDF idéntico al Word usando LibreOffice sin interfaz ({@code soffice --headless --convert-to pdf}).
 *
 * <p>Busca {@code soffice} en {@code app.pdf.soffice} o en las rutas habituales de Windows y Linux. Convierte de
 * a uno por vez para no saturar la PC.
 */
@Component
class ConversorLibreOffice implements ConversorPdf {

    private static final Logger LOG = LoggerFactory.getLogger(ConversorLibreOffice.class);
    private static final List<String> RUTAS_HABITUALES = List.of(
            "C:/Program Files/LibreOffice/program/soffice.exe",
            "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
            "/usr/bin/soffice",
            "/usr/lib/libreoffice/program/soffice",
            "/Applications/LibreOffice.app/Contents/MacOS/soffice");

    private final Path soffice;
    private final Semaphore turno = new Semaphore(1);

    ConversorLibreOffice(@Value("${app.pdf.soffice:}") String configurado) {
        this.soffice = buscar(configurado);
        if (soffice == null) {
            LOG.warn("LibreOffice no está instalado: los documentos se generarán solo en Word (DOCX).");
        } else {
            LOG.info("PDF con LibreOffice: {}", soffice);
        }
    }

    @Override
    public String estado() {
        return soffice == null
                ? "PDF no disponible: instale LibreOffice en el servidor para generar PDF automáticamente."
                : null;
    }

    @Override
    public Optional<byte[]> convertir(byte[] docx) {
        if (soffice == null) {
            return Optional.empty();
        }
        Path carpeta = null;
        try {
            turno.acquire();
            carpeta = Files.createTempDirectory("estudio-pdf-");
            Path entrada = Files.write(carpeta.resolve("documento.docx"), docx);
            Path perfil = carpeta.resolve("perfil");
            Process proceso = new ProcessBuilder(
                            soffice.toString(),
                            "-env:UserInstallation=" + perfil.toUri(),
                            "--headless",
                            "--convert-to",
                            "pdf",
                            "--outdir",
                            carpeta.toString(),
                            entrada.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(carpeta.resolve("salida.log").toFile())
                    .start();
            if (!proceso.waitFor(90, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                LOG.warn("LibreOffice tardó demasiado; documento sin PDF.");
                return Optional.empty();
            }
            Path pdf = carpeta.resolve("documento.pdf");
            return Files.exists(pdf) ? Optional.of(Files.readAllBytes(pdf)) : Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            LOG.warn("No se pudo convertir a PDF: {}", e.getMessage());
            return Optional.empty();
        } finally {
            turno.release();
            if (carpeta != null) {
                try {
                    FileSystemUtils.deleteRecursively(carpeta);
                } catch (IOException e) {
                    LOG.debug("No se pudo borrar la carpeta temporal {}", carpeta);
                }
            }
        }
    }

    private static Path buscar(String configurado) {
        if (configurado != null && !configurado.isBlank()) {
            Path ruta = Path.of(configurado);
            return Files.isExecutable(ruta) ? ruta : null;
        }
        return RUTAS_HABITUALES.stream()
                .map(Path::of)
                .filter(Files::isExecutable)
                .findFirst()
                .orElse(null);
    }
}
