package pe.estudiodocumental.auditoria.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Una acción registrada. Solo se crea; nunca se modifica. */
@Entity
@Table(name = "auditoria")
public class RegistroAuditoria extends EntidadBase {

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nombre", length = 120)
    private String usuarioNombre;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(nullable = false, length = 40)
    private String entidad;

    @Column(name = "entidad_id")
    private UUID entidadId;

    @Column(length = 500)
    private String detalle;

    protected RegistroAuditoria() {
        // Requerido por JPA
    }

    public RegistroAuditoria(
            UUID usuarioId, String usuarioNombre, String accion, String entidad, UUID entidadId, String detalle) {
        this.usuarioId = usuarioId;
        this.usuarioNombre = usuarioNombre;
        this.accion = accion;
        this.entidad = entidad;
        this.entidadId = entidadId;
        this.detalle = detalle == null || detalle.length() <= 500 ? detalle : detalle.substring(0, 500);
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getUsuarioNombre() {
        return usuarioNombre;
    }

    public String getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public UUID getEntidadId() {
        return entidadId;
    }

    public String getDetalle() {
        return detalle;
    }
}
