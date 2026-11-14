package pe.estudiodocumental.clientes.infraestructura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.estudiodocumental.clientes.dominio.Cliente;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByTipoDocumentoAndNumeroDocumento(TipoDocumento tipoDocumento, String numeroDocumento);

    /** Activos cuyo número de documento empieza con {@code texto} o cuyo nombre lo contiene. */
    @Query("""
            select c from Cliente c
            where c.activo = true
              and (:texto = ''
                   or c.numeroDocumento like concat(upper(:texto), '%')
                   or lower(concat(coalesce(c.nombres, ''), ' ', coalesce(c.apellidoPaterno, ''), ' ',
                                   coalesce(c.apellidoMaterno, ''), ' ', coalesce(c.razonSocial, '')))
                      like concat('%', lower(:texto), '%'))
            """)
    Page<Cliente> buscar(@Param("texto") String texto, Pageable pagina);
}
