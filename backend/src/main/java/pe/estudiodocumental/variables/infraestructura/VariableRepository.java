package pe.estudiodocumental.variables.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.estudiodocumental.variables.dominio.Variable;

public interface VariableRepository extends JpaRepository<Variable, UUID> {

    Optional<Variable> findByClave(String clave);

    List<Variable> findByClaveIn(Collection<String> claves);

    List<Variable> findAllByOrderByEtiquetaAsc();
}
