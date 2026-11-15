package pe.estudiodocumental.identidad.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/**
 * Sesión de un usuario en un navegador. Permite renovar el token de acceso sin volver a escribir la contraseña. Cada
 * renovación revoca esta sesión y crea otra (rotación): si alguien reutiliza un token viejo, se detecta.
 */
@Entity
@Table(name = "sesion")
public class Sesion extends EntidadBase {

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "hash_token", nullable = false, unique = true, length = 64)
    private String hashToken;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private boolean revocada;

    protected Sesion() {
        // Requerido por JPA
    }

    public Sesion(UUID usuarioId, String hashToken, Instant expiraEn) {
        this.usuarioId = usuarioId;
        this.hashToken = hashToken;
        this.expiraEn = expiraEn;
    }

    public boolean vigente(Instant ahora) {
        return !revocada && expiraEn.isAfter(ahora);
    }

    public void revocar() {
        this.revocada = true;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public boolean isRevocada() {
        return revocada;
    }
}
