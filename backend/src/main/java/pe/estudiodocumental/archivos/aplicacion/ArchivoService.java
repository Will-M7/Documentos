package pe.estudiodocumental.archivos.aplicacion;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.archivos.dominio.Archivo;
import pe.estudiodocumental.archivos.infraestructura.AlmacenArchivos;
import pe.estudiodocumental.archivos.infraestructura.ArchivoRepository;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;

/** Guardar y leer archivos. Puerta de entrada del módulo para plantillas y documentos. */
@Service
public class ArchivoService {

    public static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MIME_PDF = "application/pdf";

    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;

    public ArchivoService(ArchivoRepository archivos, AlmacenArchivos almacen) {
        this.archivos = archivos;
        this.almacen = almacen;
    }

    @Transactional
    public Archivo guardar(String nombreOriginal, String tipoMime, byte[] contenido) {
        String ruta = almacen.guardar(contenido, extension(nombreOriginal));
        return archivos.save(new Archivo(nombreOriginal, tipoMime, contenido.length, sha256(contenido), ruta));
    }

    @Transactional(readOnly = true)
    public Contenido leer(UUID id) {
        Archivo archivo =
                archivos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("El archivo no existe."));
        return new Contenido(archivo.getNombreOriginal(), archivo.getTipoMime(), almacen.leer(archivo.getRuta()));
    }

    /** Contenido de un archivo listo para enviar o procesar. */
    public record Contenido(String nombre, String tipoMime, byte[] bytes) {}

    private static String extension(String nombre) {
        int punto = nombre == null ? -1 : nombre.lastIndexOf('.');
        String ext = punto < 0 ? "bin" : nombre.substring(punto + 1).toLowerCase();
        return ext.matches("[a-z0-9]{1,8}") ? ext : "bin";
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
