package pe.estudiodocumental.auditoria.infraestructura;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.estudiodocumental.auditoria.dominio.RegistroAuditoria;

public interface AuditoriaRepository extends JpaRepository<RegistroAuditoria, UUID> {

    @Query("""
            select r from RegistroAuditoria r
            where r.creadoEn >= :desde and r.creadoEn < :hasta
              and (:texto = ''
                   or lower(r.accion) like concat('%', lower(:texto), '%')
                   or lower(coalesce(r.usuarioNombre, '')) like concat('%', lower(:texto), '%')
                   or lower(coalesce(r.detalle, '')) like concat('%', lower(:texto), '%'))
            """)
    Page<RegistroAuditoria> buscar(
            @Param("texto") String texto,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pagina);
}
