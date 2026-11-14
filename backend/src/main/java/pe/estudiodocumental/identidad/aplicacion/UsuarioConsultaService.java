package pe.estudiodocumental.identidad.aplicacion;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.identidad.dominio.Usuario;
import pe.estudiodocumental.identidad.infraestructura.UsuarioRepository;

/** Consultas de usuarios que necesitan otros módulos (por ejemplo, quién generó un documento). */
@Service
public class UsuarioConsultaService {

    private final UsuarioRepository usuarios;

    public UsuarioConsultaService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> nombres(Collection<UUID> ids) {
        return usuarios.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNombre));
    }
}
