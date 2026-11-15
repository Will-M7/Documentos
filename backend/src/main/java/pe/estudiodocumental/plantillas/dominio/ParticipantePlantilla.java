package pe.estudiodocumental.plantillas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Un rol dentro de la plantilla (otorgante, apoderado...) y qué datos suyos usa el Word. */
@Entity
@Table(name = "plantilla_participante")
public class ParticipantePlantilla extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id")
    private Plantilla plantilla;

    @Column(nullable = false, length = 60)
    private String clave;

    @Column(nullable = false, length = 120)
    private String etiqueta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cliente", nullable = false, length = 12)
    private TipoParticipante tipoCliente;

    @Column(nullable = false, length = 500)
    private String campos;

    @Column(nullable = false)
    private int orden;

    protected ParticipantePlantilla() {
        // Requerido por JPA
    }

    ParticipantePlantilla(Plantilla plantilla, String clave, List<String> campos, int orden) {
        this.plantilla = plantilla;
        this.clave = clave;
        this.etiqueta =
                Character.toUpperCase(clave.charAt(0)) + clave.substring(1).replace('_', ' ');
        this.tipoCliente = TipoParticipante.CUALQUIERA;
        this.orden = orden;
        cambiarCampos(campos);
    }

    void cambiarCampos(List<String> campos) {
        this.campos = String.join(",", campos);
    }

    void cambiarOrden(int orden) {
        this.orden = orden;
    }

    public void configurar(String etiqueta, TipoParticipante tipoCliente) {
        if (etiqueta != null && !etiqueta.isBlank()) {
            this.etiqueta = etiqueta.strip();
        }
        if (tipoCliente != null) {
            this.tipoCliente = tipoCliente;
        }
    }

    public String getClave() {
        return clave;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public TipoParticipante getTipoCliente() {
        return tipoCliente;
    }

    public List<String> getCampos() {
        return campos.isEmpty() ? List.of() : Arrays.asList(campos.split(","));
    }

    public int getOrden() {
        return orden;
    }
}
