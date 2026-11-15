package pe.estudiodocumental.documentos.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Quién ocupó cada rol en el documento, con su nombre tal como estaba al generarlo. */
@Entity
@Table(name = "documento_participante")
public class DocumentoParticipante extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    @Column(nullable = false, length = 60)
    private String participante;

    @Column(nullable = false, length = 120)
    private String etiqueta;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(nullable = false, length = 250)
    private String nombre;

    @Column(name = "documento_identidad", nullable = false, length = 30)
    private String documentoIdentidad;

    protected DocumentoParticipante() {
        // Requerido por JPA
    }

    DocumentoParticipante(
            Documento documento,
            String participante,
            String etiqueta,
            UUID clienteId,
            String nombre,
            String documentoIdentidad) {
        this.documento = documento;
        this.participante = participante;
        this.etiqueta = etiqueta;
        this.clienteId = clienteId;
        this.nombre = nombre;
        this.documentoIdentidad = documentoIdentidad;
    }

    public String getParticipante() {
        return participante;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDocumentoIdentidad() {
        return documentoIdentidad;
    }
}
