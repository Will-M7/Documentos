package pe.estudiodocumental.identidad.infraestructura;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.estudiodocumental.identidad.dominio.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);
}
