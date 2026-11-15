package pe.estudiodocumental.identidad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
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

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos;

    @Column(name = "bloqueado_hasta")
    private Instant bloqueadoHasta;

    /** Intentos fallidos seguidos que bloquean el usuario. */
    public static final int MAXIMO_INTENTOS = 5;

    /** Cuánto dura el bloqueo. */
    public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    protected Usuario() {
        // Requerido por JPA
    }

    public Usuario(String nombreUsuario, String nombre, String hashClave, Rol rol) {
        this.nombreUsuario = normalizar(nombreUsuario);
        this.nombre = nombre.strip();
        this.hashClave = hashClave;
        this.rol = rol;
    }

    public void editar(String nombre, Rol rol, boolean activo) {
        if (nombre != null && !nombre.isBlank()) {
            this.nombre = nombre.strip();
        }
        this.rol = rol;
        this.activo = activo;
    }

    public void cambiarClave(String nuevoHash) {
        this.hashClave = nuevoHash;
        desbloquear();
    }

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /** Registra un intento fallido; al llegar al máximo, bloquea el usuario un tiempo. */
    public void registrarIntentoFallido(Instant ahora) {
        intentosFallidos++;
        if (intentosFallidos >= MAXIMO_INTENTOS) {
            bloqueadoHasta = ahora.plus(DURACION_BLOQUEO);
            intentosFallidos = 0;
        }
    }

    public void desbloquear() {
        intentosFallidos = 0;
        bloqueadoHasta = null;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
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
