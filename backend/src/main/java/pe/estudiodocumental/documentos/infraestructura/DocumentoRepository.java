package pe.estudiodocumental.documentos.infraestructura;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.estudiodocumental.documentos.dominio.Documento;

public interface DocumentoRepository extends JpaRepository<Documento, UUID> {

    /** Historial: filtra por texto (título, plantilla, participante o documento de identidad) y fechas. */
    @Query("""
            select d from Documento d
            where d.creadoEn >= :desde and d.creadoEn < :hasta
              and (:texto = ''
                   or lower(d.titulo) like concat('%', lower(:texto), '%')
                   or lower(d.plantillaNombre) like concat('%', lower(:texto), '%')
                   or exists (select 1 from DocumentoParticipante p
                              where p.documento = d
                                and (lower(p.nombre) like concat('%', lower(:texto), '%')
                                     or upper(p.documentoIdentidad) like concat('%', upper(:texto), '%'))))
            """)
    Page<Documento> buscar(
            @Param("texto") String texto,
            @Param("desde") Instant desde,
            @Param("hasta") Instant hasta,
            Pageable pagina);
}
