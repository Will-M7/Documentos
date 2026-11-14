package pe.estudiodocumental.identidad.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

    public AutenticacionService(
            UsuarioRepository usuarios,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            SeguridadProperties propiedades) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.propiedades = propiedades;
    }

    @Transactional(readOnly = true)
    public TokenEmitido iniciarSesion(String nombreUsuario, String clave) {
        Usuario usuario = usuarios.findByNombreUsuario(Usuario.normalizar(nombreUsuario))
                .filter(Usuario::isActivo)
                .filter(u -> passwordEncoder.matches(clave, u.getHashClave()))
                .orElseThrow(() -> new NoAutenticadoException(CREDENCIALES_INVALIDAS));
        return emitirToken(usuario);
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
