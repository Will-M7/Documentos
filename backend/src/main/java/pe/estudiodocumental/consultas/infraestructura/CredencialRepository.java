package pe.estudiodocumental.consultas.infraestructura;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.estudiodocumental.consultas.dominio.CredencialApi;

public interface CredencialRepository extends JpaRepository<CredencialApi, UUID> {

    List<CredencialApi> findAllByOrderByPrioridadAscCreadoEnAsc();

    List<CredencialApi> findByActivoTrueOrderByPrioridadAscCreadoEnAsc();
}
