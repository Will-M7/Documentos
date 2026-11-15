package pe.estudiodocumental.solicitudes.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Pedido de un documento hecho por el cliente desde el portal. La oficina lo revisa y lo atiende o rechaza. */
@Entity
@Table(name = "solicitud")
public class Solicitud extends EntidadBase {

    @Column(nullable = false, unique = true, length = 12)
    private String codigo;

    @Column(name = "plantilla_id", nullable = false)
    private UUID plantillaId;

    @Column(name = "plantilla_nombre", nullable = false, length = 150)
    private String plantillaNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(nullable = false, columnDefinition = "text")
    private String datos;

    @Column(name = "contacto_nombre", nullable = false, length = 150)
    private String contactoNombre;

    @Column(name = "contacto_telefono", length = 30)
    private String contactoTelefono;

    @Column(name = "contacto_correo", length = 120)
    private String contactoCorreo;

    @Column(name = "acepta_datos", nullable = false)
    private boolean aceptaDatos;

    @Column(name = "motivo_rechazo", length = 300)
    private String motivoRechazo;

    @Column(name = "documento_id")
    private UUID documentoId;

    @Column(name = "atendida_por")
    private UUID atendidaPor;

    @Column(name = "atendida_en")
    private Instant atendidaEn;

    protected Solicitud() {
        // Requerido por JPA
    }

    public Solicitud(
            String codigo,
            UUID plantillaId,
            String plantillaNombre,
            String datos,
            String contactoNombre,
            String contactoTelefono,
            String contactoCorreo,
            boolean aceptaDatos) {
        if (!aceptaDatos) {
            throw new ReglaNegocioException("Debe autorizar el tratamiento de sus datos personales para continuar.");
        }
        if (contactoNombre == null || contactoNombre.isBlank()) {
            throw new ReglaNegocioException("Escriba su nombre para que la oficina pueda contactarle.");
        }
        if (vacio(contactoTelefono) && vacio(contactoCorreo)) {
            throw new ReglaNegocioException("Deje un teléfono o un correo para avisarle cuando esté listo.");
        }
        this.codigo = codigo;
        this.plantillaId = plantillaId;
        this.plantillaNombre = plantillaNombre;
        this.datos = datos;
        this.contactoNombre = contactoNombre.strip();
        this.contactoTelefono = vacio(contactoTelefono) ? null : contactoTelefono.strip();
        this.contactoCorreo = vacio(contactoCorreo) ? null : contactoCorreo.strip();
        this.aceptaDatos = true;
    }

    public void atender(UUID documentoId, UUID usuarioId) {
        if (estado != EstadoSolicitud.PENDIENTE) {
            return;
        }
        this.estado = EstadoSolicitud.ATENDIDA;
        this.documentoId = documentoId;
        this.atendidaPor = usuarioId;
        this.atendidaEn = Instant.now();
    }

    public void rechazar(String motivo, UUID usuarioId) {
        if (estado != EstadoSolicitud.PENDIENTE) {
            throw new ReglaNegocioException("Solo se pueden rechazar solicitudes pendientes.");
        }
        if (vacio(motivo)) {
            throw new ReglaNegocioException("Escriba el motivo del rechazo: el cliente lo verá al consultar.");
        }
        this.estado = EstadoSolicitud.RECHAZADA;
        this.motivoRechazo = motivo.strip();
        this.atendidaPor = usuarioId;
        this.atendidaEn = Instant.now();
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    public String getCodigo() {
        return codigo;
    }

    public UUID getPlantillaId() {
        return plantillaId;
    }

    public String getPlantillaNombre() {
        return plantillaNombre;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public String getDatos() {
        return datos;
    }

    public String getContactoNombre() {
        return contactoNombre;
    }

    public String getContactoTelefono() {
        return contactoTelefono;
    }

    public String getContactoCorreo() {
        return contactoCorreo;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public UUID getDocumentoId() {
        return documentoId;
    }

    public Instant getAtendidaEn() {
        return atendidaEn;
    }
}
