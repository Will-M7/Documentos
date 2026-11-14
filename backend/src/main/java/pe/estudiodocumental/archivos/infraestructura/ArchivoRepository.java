package pe.estudiodocumental.archivos.infraestructura;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.estudiodocumental.archivos.dominio.Archivo;

public interface ArchivoRepository extends JpaRepository<Archivo, UUID> {}
