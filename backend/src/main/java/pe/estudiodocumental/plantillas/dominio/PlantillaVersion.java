package pe.estudiodocumental.plantillas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Un DOCX subido. Inmutable: corregir la plantilla crea una versión nueva. */
@Entity
@Table(name = "plantilla_version")
public class PlantillaVersion extends EntidadBase {

    @Column(name = "plantilla_id", nullable = false)
    private UUID plantillaId;

    @Column(nullable = false)
    private int numero;

    @Column(name = "archivo_id", nullable = false)
    private UUID archivoId;

    @Column(nullable = false, length = 4000)
    private String marcas;

    protected PlantillaVersion() {
        // Requerido por JPA
    }

    public PlantillaVersion(UUID plantillaId, int numero, UUID archivoId, List<Marca> marcas) {
        this.plantillaId = plantillaId;
        this.numero = numero;
        this.archivoId = archivoId;
        this.marcas = String.join("\n", marcas.stream().map(Marca::texto).toList());
    }

    public UUID getPlantillaId() {
        return plantillaId;
    }

    public int getNumero() {
        return numero;
    }

    public UUID getArchivoId() {
        return archivoId;
    }

    public List<String> getMarcas() {
        return marcas.isEmpty() ? List.of() : Arrays.asList(marcas.split("\n"));
    }
}
