package pe.estudiodocumental.plantillas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Una variable del catálogo usada por la versión actual de la plantilla. */
@Entity
@Table(name = "plantilla_variable")
public class VariablePlantilla extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id")
    private Plantilla plantilla;

    /** Referencia por id al módulo variables (no se cruza la frontera del módulo con una relación JPA). */
    @Column(name = "variable_id", nullable = false)
    private UUID variableId;

    @Column(nullable = false)
    private int orden;

    protected VariablePlantilla() {
        // Requerido por JPA
    }

    VariablePlantilla(Plantilla plantilla, UUID variableId, int orden) {
        this.plantilla = plantilla;
        this.variableId = variableId;
        this.orden = orden;
    }

    void cambiarOrden(int orden) {
        this.orden = orden;
    }

    public UUID getVariableId() {
        return variableId;
    }

    public int getOrden() {
        return orden;
    }
}
