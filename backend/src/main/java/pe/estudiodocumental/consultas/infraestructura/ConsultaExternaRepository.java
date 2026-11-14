package pe.estudiodocumental.consultas.infraestructura;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.estudiodocumental.consultas.dominio.ConsultaExterna;

public interface ConsultaExternaRepository extends JpaRepository<ConsultaExterna, UUID> {

    @Query("select count(c) from ConsultaExterna c where c.creadoEn >= :desde")
    long contarDesde(Instant desde);
}
