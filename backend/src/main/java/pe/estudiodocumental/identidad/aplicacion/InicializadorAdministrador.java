package pe.estudiodocumental.identidad.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pe.estudiodocumental.identidad.dominio.Rol;
import pe.estudiodocumental.identidad.dominio.Usuario;
import pe.estudiodocumental.identidad.infraestructura.AdminInicialProperties;
import pe.estudiodocumental.identidad.infraestructura.UsuarioRepository;

/**
 * Al arrancar con la base vacía, crea el primer administrador con los datos de {@code ADMIN_USUARIO} y
 * {@code ADMIN_CLAVE}. Si ya existe algún usuario, no hace nada.
 */
@Component
class InicializadorAdministrador implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorAdministrador.class);
    private static final int LONGITUD_MINIMA_CLAVE = 10;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AdminInicialProperties admin;

    InicializadorAdministrador(
            UsuarioRepository usuarios, PasswordEncoder passwordEncoder, AdminInicialProperties admin) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.admin = admin;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(admin.usuario()) || !StringUtils.hasText(admin.clave())) {
            LOG.warn("No hay usuarios. Defina ADMIN_USUARIO y ADMIN_CLAVE para crear el primer administrador.");
            return;
        }
        if (admin.clave().length() < LONGITUD_MINIMA_CLAVE) {
            throw new IllegalStateException(
                    "ADMIN_CLAVE debe tener al menos " + LONGITUD_MINIMA_CLAVE + " caracteres.");
        }
        String nombre = StringUtils.hasText(admin.nombre()) ? admin.nombre() : "Administrador";
        usuarios.save(new Usuario(admin.usuario(), nombre, passwordEncoder.encode(admin.clave()), Rol.ADMIN));
        LOG.info("Administrador inicial '{}' creado.", Usuario.normalizar(admin.usuario()));
    }
}
