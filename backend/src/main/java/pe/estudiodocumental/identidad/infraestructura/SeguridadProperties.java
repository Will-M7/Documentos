package pe.estudiodocumental.identidad.infraestructura;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de seguridad ({@code app.seguridad.*}).
 *
 * @param jwtSecreto clave para firmar los tokens; mínimo 32 caracteres. Viene de la variable de entorno
 *     {@code JWT_SECRETO}, nunca del código.
 * @param duracionToken cuánto dura un token desde que se emite.
 */
@Validated
@ConfigurationProperties("app.seguridad")
public record SeguridadProperties(
        @NotNull @Size(min = 32, message = "JWT_SECRETO debe tener al menos 32 caracteres")
        String jwtSecreto,

        @NotNull Duration duracionToken) {}
