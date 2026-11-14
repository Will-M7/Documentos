package pe.estudiodocumental.variables.aplicacion;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.comun.error.ConflictoException;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;
import pe.estudiodocumental.variables.dominio.TipoVariable;
import pe.estudiodocumental.variables.dominio.Variable;
import pe.estudiodocumental.variables.infraestructura.VariableRepository;

/** Casos de uso del catálogo de variables. Puerta de entrada del módulo para los demás. */
@Service
public class VariableService {

    private final VariableRepository variables;

    public VariableService(VariableRepository variables) {
        this.variables = variables;
    }

    @Transactional(readOnly = true)
    public List<Variable> listar() {
        return variables.findAllByOrderByEtiquetaAsc();
    }

    @Transactional(readOnly = true)
    public Variable obtener(UUID id) {
        return variables.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("La variable no existe."));
    }

    @Transactional
    public Variable crear(String clave, String etiqueta, TipoVariable tipo, String valorDefecto, String descripcion) {
        Variable nueva = new Variable(clave, etiqueta, tipo, valorDefecto, descripcion);
        if (variables.findByClave(nueva.getClave()).isPresent()) {
            throw new ConflictoException("Ya existe la variable {{%s}}.".formatted(nueva.getClave()));
        }
        return variables.save(nueva);
    }

    @Transactional
    public Variable editar(UUID id, String etiqueta, TipoVariable tipo, String valorDefecto, String descripcion) {
        Variable variable = obtener(id);
        variable.editar(etiqueta, tipo, valorDefecto, descripcion);
        return variable;
    }

    /**
     * Devuelve las variables de esas claves, creando con valores por defecto las que todavía no existen. Lo usa
     * el módulo de plantillas al detectar marcas nuevas en un DOCX.
     */
    @Transactional
    public Map<String, Variable> asegurar(Collection<String> claves) {
        Set<String> normalizadas =
                claves.stream().map(Variable::normalizarClave).collect(Collectors.toSet());
        Map<String, Variable> existentes = variables.findByClaveIn(normalizadas).stream()
                .collect(Collectors.toMap(Variable::getClave, Function.identity()));
        Set<String> faltantes = new HashSet<>(normalizadas);
        faltantes.removeAll(existentes.keySet());
        for (String clave : faltantes) {
            existentes.put(clave, variables.save(Variable.detectada(clave)));
        }
        Map<String, Variable> ordenadas = new LinkedHashMap<>();
        claves.forEach(c -> ordenadas.put(Variable.normalizarClave(c), existentes.get(Variable.normalizarClave(c))));
        return ordenadas;
    }

    @Transactional(readOnly = true)
    public Map<String, Variable> porClaves(Collection<String> claves) {
        return variables.findByClaveIn(claves).stream()
                .collect(Collectors.toMap(Variable::getClave, Function.identity()));
    }
}
