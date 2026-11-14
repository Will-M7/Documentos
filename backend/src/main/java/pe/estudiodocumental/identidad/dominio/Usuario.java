package pe.estudiodocumental.identidad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Persona que entra al sistema. La contraseña se guarda solo como hash BCrypt. */
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadBase {

    @Column(name = "usuario", nullable = false, unique = true, length = 50)
    private String nombreUsuario;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "hash_clave", nullable = false, length = 100)
    private String hashClave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    protected Usuario() {
        // Requerido por JPA
    }

    public Usuario(String nombreUsuario, String nombre, String hashClave, Rol rol) {
        this.nombreUsuario = normalizar(nombreUsuario);
        this.nombre = nombre.strip();
        this.hashClave = hashClave;
        this.rol = rol;
    }

    /** Los nombres de usuario no distinguen mayúsculas ni espacios exteriores. */
    public static String normalizar(String nombreUsuario) {
        return nombreUsuario.strip().toLowerCase(Locale.ROOT);
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public String getHashClave() {
        return hashClave;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }
}
