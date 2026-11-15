package pe.estudiodocumental.identidad.infraestructura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pe.estudiodocumental.identidad.dominio.Sesion;

public interface SesionRepository extends JpaRepository<Sesion, UUID> {

    Optional<Sesion> findByHashToken(String hashToken);

    /** Cierra todas las sesiones del usuario (desactivado, clave restablecida o token robado). */
    @Modifying
    @Query("update Sesion s set s.revocada = true where s.usuarioId = :usuarioId and s.revocada = false")
    int revocarTodas(UUID usuarioId);
}
