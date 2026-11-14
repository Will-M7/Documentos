package pe.estudiodocumental.archivos.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Datos de un archivo guardado. El contenido está en el almacén (disco), no en la base. */
@Entity
@Table(name = "archivo")
public class Archivo extends EntidadBase {

    @Column(name = "nombre_original", nullable = false)
    private String nombreOriginal;

    @Column(name = "tipo_mime", nullable = false, length = 120)
    private String tipoMime;

    @Column(nullable = false)
    private long tamano;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false, unique = true, length = 500)
    private String ruta;

    protected Archivo() {
        // Requerido por JPA
    }

    public Archivo(String nombreOriginal, String tipoMime, long tamano, String sha256, String ruta) {
        this.nombreOriginal = nombreOriginal;
        this.tipoMime = tipoMime;
        this.tamano = tamano;
        this.sha256 = sha256;
        this.ruta = ruta;
    }

    public String getNombreOriginal() {
        return nombreOriginal;
    }

    public String getTipoMime() {
        return tipoMime;
    }

    public long getTamano() {
        return tamano;
    }

    public String getSha256() {
        return sha256;
    }

    public String getRuta() {
        return ruta;
    }
}
