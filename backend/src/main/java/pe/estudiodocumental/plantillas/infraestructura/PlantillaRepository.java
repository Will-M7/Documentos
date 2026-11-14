package pe.estudiodocumental.plantillas.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.estudiodocumental.plantillas.dominio.Plantilla;
import pe.estudiodocumental.plantillas.dominio.PlantillaVersion;

public interface PlantillaRepository extends JpaRepository<Plantilla, UUID> {

    @Query("select p from Plantilla p where lower(p.nombre) = lower(:nombre)")
    Optional<Plantilla> buscarPorNombre(String nombre);

    List<Plantilla> findAllByOrderByCategoriaAscNombreAsc();

    List<Plantilla> findByActivoTrueOrderByCategoriaAscNombreAsc();

    @Query("select v from PlantillaVersion v where v.plantillaId = :plantillaId order by v.numero desc")
    List<PlantillaVersion> versiones(UUID plantillaId);

    @Query("select v from PlantillaVersion v where v.plantillaId = :plantillaId and v.numero = :numero")
    Optional<PlantillaVersion> version(UUID plantillaId, int numero);
}
