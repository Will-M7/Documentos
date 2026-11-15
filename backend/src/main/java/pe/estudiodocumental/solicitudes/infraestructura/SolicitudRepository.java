package pe.estudiodocumental.solicitudes.infraestructura;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.estudiodocumental.solicitudes.dominio.EstadoSolicitud;
import pe.estudiodocumental.solicitudes.dominio.Solicitud;

public interface SolicitudRepository extends JpaRepository<Solicitud, UUID> {

    Optional<Solicitud> findByCodigo(String codigo);

    /** La lee bloqueándola hasta el fin de la transacción: dos operadores no pueden atenderla a la vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solicitud s where s.id = :id")
    Optional<Solicitud> buscarParaActualizar(@Param("id") UUID id);

    Page<Solicitud> findByEstado(EstadoSolicitud estado, Pageable pagina);

    long countByEstado(EstadoSolicitud estado);
}
