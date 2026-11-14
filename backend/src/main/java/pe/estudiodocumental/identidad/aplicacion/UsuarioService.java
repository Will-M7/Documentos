package pe.estudiodocumental.identidad.aplicacion;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.comun.error.ConflictoException;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.identidad.dominio.Rol;
import pe.estudiodocumental.identidad.dominio.Usuario;
import pe.estudiodocumental.identidad.infraestructura.UsuarioRepository;

/** Administración de usuarios (solo ADMIN). */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return usuarios.findAll(Sort.by("activo").descending().and(Sort.by("nombre")));
    }

    @Transactional
    public Usuario crear(String nombreUsuario, String nombre, String clave, Rol rol) {
        if (nombreUsuario == null || !Usuario.normalizar(nombreUsuario).matches("[a-z0-9._-]{3,50}")) {
            throw new ReglaNegocioException(
                    "El usuario debe tener de 3 a 50 letras, números, punto, guion o guion bajo, sin espacios.");
        }
        if (usuarios.findByNombreUsuario(Usuario.normalizar(nombreUsuario)).isPresent()) {
            throw new ConflictoException("Ya existe el usuario '%s'.".formatted(Usuario.normalizar(nombreUsuario)));
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Escriba el nombre de la persona.");
        }
        return usuarios.save(
                new Usuario(nombreUsuario, nombre, passwordEncoder.encode(PoliticaClave.validar(clave)), rol));
    }

    /**
     * Edita nombre, rol y estado. Un administrador no puede quitarse a sí mismo el rol ni desactivarse, para que
     * siempre quede alguien que administre.
     */
    @Transactional
    public Usuario editar(UUID id, String nombre, Rol rol, boolean activo, UUID quienEdita) {
        Usuario usuario = obtener(id);
        if (id.equals(quienEdita) && (rol != Rol.ADMIN || !activo)) {
            throw new ReglaNegocioException("No puede quitarse el rol de administrador ni desactivarse a sí mismo.");
        }
        usuario.editar(nombre, rol, activo);
        return usuario;
    }

    /** El administrador asigna una contraseña nueva (por ejemplo, si alguien la olvidó) y desbloquea. */
    @Transactional
    public void restablecerClave(UUID id, String nueva) {
        obtener(id).cambiarClave(passwordEncoder.encode(PoliticaClave.validar(nueva)));
    }

    private Usuario obtener(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
    }
}
