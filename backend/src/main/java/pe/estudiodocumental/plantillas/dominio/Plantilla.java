package pe.estudiodocumental.plantillas.dominio;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/**
 * Plantilla de documento. Sus participantes y variables reflejan la versión actual: al subir una versión nueva se
 * recalculan, conservando la configuración (etiqueta, tipo) de los participantes que siguen existiendo.
 */
@Entity
@Table(name = "plantilla")
public class Plantilla extends EntidadBase {

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(length = 80)
    private String categoria;

    @Column(name = "version_actual", nullable = false)
    private int versionActual;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden")
    private List<ParticipantePlantilla> participantes = new ArrayList<>();

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden")
    private List<VariablePlantilla> variables = new ArrayList<>();

    protected Plantilla() {
        // Requerido por JPA
    }

    public Plantilla(String nombre, String descripcion, String categoria) {
        editar(nombre, descripcion, categoria);
        this.versionActual = 0;
    }

    public void editar(String nombre, String descripcion, String categoria) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre de la plantilla.");
        }
        this.nombre = nombre.strip();
        this.descripcion = vacioANulo(descripcion);
        this.categoria = vacioANulo(categoria);
    }

    public void activar(boolean activo) {
        this.activo = activo;
    }

    /**
     * Registra una versión nueva con sus marcas ya separadas.
     *
     * @param camposPorParticipante campos usados por cada participante, en orden de aparición.
     * @param variablesIds variables del catálogo usadas, en orden de aparición.
     * @return el número de la nueva versión.
     */
    public int nuevaVersion(Map<String, List<String>> camposPorParticipante, List<UUID> variablesIds) {
        Map<String, ParticipantePlantilla> actuales = new LinkedHashMap<>();
        participantes.forEach(p -> actuales.put(p.getClave(), p));
        participantes.removeIf(p -> !camposPorParticipante.containsKey(p.getClave()));
        int orden = 0;
        for (var entrada : camposPorParticipante.entrySet()) {
            ParticipantePlantilla existente = actuales.get(entrada.getKey());
            if (existente != null) {
                existente.cambiarCampos(entrada.getValue());
                existente.cambiarOrden(orden);
            } else {
                participantes.add(new ParticipantePlantilla(this, entrada.getKey(), entrada.getValue(), orden));
            }
            orden++;
        }
        // Se reutilizan las filas existentes: borrar y volver a insertar la misma variable choca con la
        // restricción única, porque Hibernate inserta antes de borrar.
        Map<UUID, VariablePlantilla> variablesActuales = new LinkedHashMap<>();
        variables.forEach(v -> variablesActuales.put(v.getVariableId(), v));
        variables.removeIf(v -> !variablesIds.contains(v.getVariableId()));
        for (int i = 0; i < variablesIds.size(); i++) {
            VariablePlantilla existente = variablesActuales.get(variablesIds.get(i));
            if (existente != null) {
                existente.cambiarOrden(i);
            } else {
                variables.add(new VariablePlantilla(this, variablesIds.get(i), i));
            }
        }
        versionActual++;
        return versionActual;
    }

    public ParticipantePlantilla participante(String clave) {
        return participantes.stream()
                .filter(p -> p.getClave().equals(clave))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("La plantilla no tiene el participante '" + clave + "'."));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCategoria() {
        return categoria;
    }

    public int getVersionActual() {
        return versionActual;
    }

    public boolean isActivo() {
        return activo;
    }

    public List<ParticipantePlantilla> getParticipantes() {
        return participantes.stream()
                .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
                .toList();
    }

    public List<VariablePlantilla> getVariables() {
        return variables.stream()
                .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
                .toList();
    }
}
