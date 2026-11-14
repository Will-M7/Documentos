package pe.estudiodocumental.identidad.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.identidad.api.dto.LoginRequest;
import pe.estudiodocumental.identidad.api.dto.TokenResponse;
import pe.estudiodocumental.identidad.api.dto.UsuarioResponse;
import pe.estudiodocumental.identidad.aplicacion.AutenticacionService;

@Tag(name = "Autenticación")
@RestController
@RequestMapping("/api/v1/auth")
class AutenticacionController {

    private final AutenticacionService autenticacion;

    AutenticacionController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Operation(summary = "Iniciar sesión", description = "Devuelve un token para enviar en Authorization: Bearer.")
    @SecurityRequirements
    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest solicitud) {
        var emitido = autenticacion.iniciarSesion(solicitud.usuario(), solicitud.clave());
        return new TokenResponse(emitido.token(), emitido.expiraEn(), UsuarioResponse.desde(emitido.usuario()));
    }

    record CambiarClaveRequest(
            @NotBlank(message = "Escriba su contraseña actual")
            String actual,

            @NotBlank(message = "Escriba la contraseña nueva")
            String nueva) {}

    @Operation(summary = "Cambiar mi contraseña")
    @PutMapping("/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cambiarClave(@Valid @RequestBody CambiarClaveRequest s, @AuthenticationPrincipal Jwt token) {
        autenticacion.cambiarMiClave(UUID.fromString(token.getSubject()), s.actual(), s.nueva());
    }

    @Operation(summary = "Usuario actual", description = "Datos del usuario dueño del token.")
    @GetMapping("/yo")
    UsuarioResponse yo(@AuthenticationPrincipal Jwt token) {
        return UsuarioResponse.desde(autenticacion.usuarioActual(UUID.fromString(token.getSubject())));
    }
}
