package pe.estudiodocumental.identidad.infraestructura;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad de la API: sin sesiones; cada petición trae un token JWT firmado con HS256.
 *
 * <p>Públicos: login, documentación de la API, estado de salud y los archivos de la web. Toda la API requiere token.
 */
@Configuration
@EnableMethodSecurity
class SeguridadConfig {

    /** Claim del token que lleva los roles del usuario. */
    static final String CLAIM_ROLES = "roles";

    @Bean
    SecurityFilterChain filtroSeguridad(HttpSecurity http) throws Exception {
        return http
                // Sin cookies de sesión, por lo que CSRF no aplica
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers("/api/docs", "/api/docs/**", "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/**", "/actuator/**")
                        .authenticated()
                        // El resto son los archivos de la web (HTML, JS, CSS), que no tienen datos
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorRoles())))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(SeguridadProperties propiedades) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(propiedades)));
    }

    @Bean
    JwtDecoder jwtDecoder(SeguridadProperties propiedades) {
        return NimbusJwtDecoder.withSecretKey(clave(propiedades))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Convierte el claim "roles" del token en ROLE_ADMIN / ROLE_OPERADOR. */
    private static JwtAuthenticationConverter convertidorRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(CLAIM_ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }

    private static SecretKey clave(SeguridadProperties propiedades) {
        return new SecretKeySpec(propiedades.jwtSecreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
