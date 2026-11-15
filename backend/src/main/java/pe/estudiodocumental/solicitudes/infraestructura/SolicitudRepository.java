package pe.estudiodocumental.solicitudes.infraestructura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.estudiodocumental.solicitudes.dominio.EstadoSolicitud;
import pe.estudiodocumental.solicitudes.dominio.Solicitud;

public interface SolicitudRepository extends JpaRepository<Solicitud, UUID> {

    Optional<Solicitud> findByCodigo(String codigo);

    Page<Solicitud> findByEstado(EstadoSolicitud estado, Pageable pagina);

    long countByEstado(EstadoSolicitud estado);
}
