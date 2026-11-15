package pe.estudiodocumental.plantillas.infraestructura;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.estudiodocumental.plantillas.dominio.PlantillaVersion;

public interface PlantillaVersionRepository extends JpaRepository<PlantillaVersion, UUID> {}
