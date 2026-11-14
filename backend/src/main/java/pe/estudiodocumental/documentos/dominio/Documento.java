package pe.estudiodocumental.documentos.dominio;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Documento generado. Guarda la versión exacta de la plantilla y los valores usados. */
@Entity
@Table(name = "documento")
public class Documento extends EntidadBase {

    /** Correlativo asignado por la base al insertar. */
    @Generated
    @Column(insertable = false, updatable = false)
    private Long numero;

    @Column(name = "plantilla_id", nullable = false)
    private UUID plantillaId;

    @Column(name = "plantilla_version_id", nullable = false)
    private UUID plantillaVersionId;

    @Column(name = "plantilla_nombre", nullable = false, length = 150)
    private String plantillaNombre;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 250)
    private String titulo;

    @Column(nullable = false, columnDefinition = "text")
    private String valores;

    @Column(name = "docx_archivo_id", nullable = false)
    private UUID docxArchivoId;

    @Column(name = "pdf_archivo_id")
    private UUID pdfArchivoId;

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("participante")
    private List<DocumentoParticipante> participantes = new ArrayList<>();

    protected Documento() {
        // Requerido por JPA
    }

    public Documento(
            UUID plantillaId,
            UUID plantillaVersionId,
            String plantillaNombre,
            UUID usuarioId,
            String titulo,
            String valores,
            UUID docxArchivoId) {
        this.plantillaId = plantillaId;
        this.plantillaVersionId = plantillaVersionId;
        this.plantillaNombre = plantillaNombre;
        this.usuarioId = usuarioId;
        this.titulo = titulo;
        this.valores = valores;
        this.docxArchivoId = docxArchivoId;
    }

    public void agregarParticipante(
            String participante, String etiqueta, UUID clienteId, String nombre, String documentoIdentidad) {
        participantes.add(
                new DocumentoParticipante(this, participante, etiqueta, clienteId, nombre, documentoIdentidad));
    }

    public void adjuntarPdf(UUID archivoId) {
        this.pdfArchivoId = archivoId;
    }

    public Long getNumero() {
        return numero;
    }

    public UUID getPlantillaId() {
        return plantillaId;
    }

    public UUID getPlantillaVersionId() {
        return plantillaVersionId;
    }

    public String getPlantillaNombre() {
        return plantillaNombre;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getValores() {
        return valores;
    }

    public UUID getDocxArchivoId() {
        return docxArchivoId;
    }

    public UUID getPdfArchivoId() {
        return pdfArchivoId;
    }

    public List<DocumentoParticipante> getParticipantes() {
        return participantes;
    }
}
