package pe.estudiodocumental.identidad.infraestructura;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Primer administrador ({@code app.admin-inicial.*}). Solo se usa si la base no tiene usuarios.
 *
 * @param usuario nombre de usuario (variable {@code ADMIN_USUARIO}).
 * @param clave contraseña inicial (variable {@code ADMIN_CLAVE}); debe cambiarse tras el primer ingreso.
 * @param nombre nombre visible.
 */
@ConfigurationProperties("app.admin-inicial")
public record AdminInicialProperties(String usuario, String clave, String nombre) {}
