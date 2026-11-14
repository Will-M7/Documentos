package pe.estudiodocumental.identidad.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.comun.error.NoAutenticadoException;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.eventos.EventoAuditoria;
import pe.estudiodocumental.identidad.dominio.Usuario;
import pe.estudiodocumental.identidad.infraestructura.SeguridadProperties;
import pe.estudiodocumental.identidad.infraestructura.UsuarioRepository;

/** Casos de uso de autenticación: iniciar sesión y consultar el usuario actual. */
@Service
public class AutenticacionService {

    /** Mismo mensaje para usuario inexistente y clave incorrecta, para no revelar qué usuarios existen. */
    private static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SeguridadProperties propiedades;

    private final ApplicationEventPublisher eventos;

    public AutenticacionService(
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            SeguridadProperties propiedades,
            ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.propiedades = propiedades;
        this.eventos = eventos;
    }

    /**
     * Tras {@link Usuario#MAXIMO_INTENTOS} contraseñas incorrectas seguidas, el usuario queda bloqueado
     * {@link Usuario#DURACION_BLOQUEO}. El contador se guarda aunque el ingreso falle.
     */
    @Transactional(noRollbackFor = NoAutenticadoException.class)
    public TokenEmitido iniciarSesion(String nombreUsuario, String clave) {
        Instant ahora = Instant.now();
        Usuario usuario = usuarios.findByNombreUsuario(Usuario.normalizar(nombreUsuario))
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new NoAutenticadoException(CREDENCIALES_INVALIDAS));
        if (usuario.estaBloqueado(ahora)) {
            long minutos = Math.max(
                    1, Duration.between(ahora, usuario.getBloqueadoHasta()).toMinutes() + 1);
            throw new NoAutenticadoException(
                    "Usuario bloqueado por varios intentos fallidos. Intente de nuevo en %d minutos."
                            .formatted(minutos));
        }
        if (!passwordEncoder.matches(clave, usuario.getHashClave())) {
            usuario.registrarIntentoFallido(ahora);
            boolean bloqueado = usuario.estaBloqueado(ahora);
            eventos.publishEvent(new EventoAuditoria(
                    bloqueado ? "USUARIO_BLOQUEADO" : "INGRESO_FALLIDO",
                    "usuario",
                    usuario.getId(),
                    usuario.getNombreUsuario(),
                    usuario.getId()));
            throw new NoAutenticadoException(CREDENCIALES_INVALIDAS);
        }
        usuario.desbloquear();
        eventos.publishEvent(new EventoAuditoria(
                "INGRESO", "usuario", usuario.getId(), usuario.getNombreUsuario(), usuario.getId()));
        return emitirToken(usuario);
    }

    /** El propio usuario cambia su contraseña indicando la actual. */
    @Transactional
    public void cambiarMiClave(UUID id, String actual, String nueva) {
        Usuario usuario = usuarioActual(id);
        if (!passwordEncoder.matches(actual, usuario.getHashClave())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        usuario.cambiarClave(passwordEncoder.encode(PoliticaClave.validar(nueva)));
        eventos.publishEvent(EventoAuditoria.de("CLAVE_CAMBIADA", "usuario", id, usuario.getNombreUsuario()));
    }

    @Transactional(readOnly = true)
    public Usuario usuarioActual(UUID id) {
        return usuarios.findById(id)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe o está inactivo."));
    }

    private TokenEmitido emitirToken(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(propiedades.duracionToken());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("estudio-documental")
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("usuario", usuario.getNombreUsuario())
                .claim("roles", List.of(usuario.getRol().name()))
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token =
                jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(token, expira, usuario);
    }

    /** Resultado de un inicio de sesión exitoso. */
    public record TokenEmitido(String token, Instant expiraEn, Usuario usuario) {}
}
