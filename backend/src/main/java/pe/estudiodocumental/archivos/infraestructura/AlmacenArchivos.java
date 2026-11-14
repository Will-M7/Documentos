package pe.estudiodocumental.archivos.infraestructura;

/**
 * Dónde se guardan físicamente los archivos. Hoy en disco ({@link AlmacenEnDisco}); para pasar a la nube (S3,
 * MinIO) basta con otra implementación, sin tocar el resto del sistema.
 */
public interface AlmacenArchivos {

    /** Guarda el contenido y devuelve su ruta relativa dentro del almacén. */
    String guardar(byte[] contenido, String extension);

    byte[] leer(String ruta);
}
