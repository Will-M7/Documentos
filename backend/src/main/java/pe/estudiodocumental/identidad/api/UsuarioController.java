package pe.estudiodocumental.identidad.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.identidad.aplicacion.UsuarioService;
import pe.estudiodocumental.identidad.dominio.Rol;
import pe.estudiodocumental.identidad.dominio.Usuario;

@Tag(name = "Usuarios")
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMIN')")
class UsuarioController {

    private final UsuarioService usuarios;

    UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    record UsuarioAdminResponse(
            UUID id, String usuario, String nombre, Rol rol, boolean activo, Instant bloqueadoHasta, Instant creadoEn) {

        static UsuarioAdminResponse desde(Usuario u) {
            Instant bloqueo = u.estaBloqueado(Instant.now()) ? u.getBloqueadoHasta() : null;
            return new UsuarioAdminResponse(
                    u.getId(), u.getNombreUsuario(), u.getNombre(), u.getRol(), u.isActivo(), bloqueo, u.getCreadoEn());
        }
    }

    record CrearUsuarioRequest(
            @NotBlank(message = "Escriba el usuario") @Size(max = 50)
            String usuario,

            @NotBlank(message = "Escriba el nombre") @Size(max = 120)
            String nombre,

            @NotBlank(message = "Escriba la contraseña inicial") @Size(max = 100)
            String clave,

            @NotNull(message = "Elija el rol") Rol rol) {}

    record EditarUsuarioRequest(
            @NotBlank(message = "Escriba el nombre") @Size(max = 120)
            String nombre,

            @NotNull(message = "Elija el rol") Rol rol,
            boolean activo) {}

    record ClaveRequest(
            @NotBlank(message = "Escriba la contraseña") @Size(max = 100)
            String clave) {}

    @GetMapping
    List<UsuarioAdminResponse> listar() {
        return usuarios.listar().stream().map(UsuarioAdminResponse::desde).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UsuarioAdminResponse crear(@Valid @RequestBody CrearUsuarioRequest s) {
        return UsuarioAdminResponse.desde(usuarios.crear(s.usuario(), s.nombre(), s.clave(), s.rol()));
    }

    @PutMapping("/{id}")
    UsuarioAdminResponse editar(
            @PathVariable UUID id, @Valid @RequestBody EditarUsuarioRequest s, @AuthenticationPrincipal Jwt token) {
        return UsuarioAdminResponse.desde(
                usuarios.editar(id, s.nombre(), s.rol(), s.activo(), UUID.fromString(token.getSubject())));
    }

    @Operation(summary = "Restablecer contraseña", description = "También desbloquea al usuario.")
    @PutMapping("/{id}/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restablecerClave(@PathVariable UUID id, @Valid @RequestBody ClaveRequest s) {
        usuarios.restablecerClave(id, s.clave());
    }
}
