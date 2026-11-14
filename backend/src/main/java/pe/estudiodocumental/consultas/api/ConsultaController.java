package pe.estudiodocumental.consultas.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.clientes.api.dto.ClienteResponse;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;
import pe.estudiodocumental.consultas.api.dto.ConsultaDtos.ConsultaResponse;
import pe.estudiodocumental.consultas.api.dto.ConsultaDtos.CrearCredencialRequest;
import pe.estudiodocumental.consultas.api.dto.ConsultaDtos.CredencialResponse;
import pe.estudiodocumental.consultas.api.dto.ConsultaDtos.EditarCredencialRequest;
import pe.estudiodocumental.consultas.api.dto.ConsultaDtos.ResumenCredenciales;
import pe.estudiodocumental.consultas.aplicacion.ConsultaService;
import pe.estudiodocumental.consultas.aplicacion.CredencialService;

@Tag(name = "Consultas DNI/RUC")
@RestController
@RequestMapping("/api/v1")
class ConsultaController {

    private final ConsultaService consultas;
    private final CredencialService credenciales;

    ConsultaController(ConsultaService consultas, CredencialService credenciales) {
        this.consultas = consultas;
        this.credenciales = credenciales;
    }

    @Operation(
            summary = "Consultar DNI o RUC",
            description = "Primero en Clientes; si no está (o con ?actualizar=true), en los proveedores configurados.")
    @PostMapping("/consultas/{tipo}/{numero}")
    ConsultaResponse consultar(
            @PathVariable TipoDocumento tipo,
            @PathVariable String numero,
            @RequestParam(defaultValue = "false") boolean actualizar,
            @AuthenticationPrincipal Jwt token) {
        var r = consultas.consultar(tipo, numero, actualizar, UUID.fromString(token.getSubject()));
        return new ConsultaResponse(
                r.cliente() != null,
                r.origen(),
                r.cliente() == null ? null : ClienteResponse.desde(r.cliente()),
                r.mensaje());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/credenciales")
    ResumenCredenciales listar() {
        return new ResumenCredenciales(
                credenciales.consultasDelMes(),
                credenciales.listar().stream().map(CredencialResponse::desde).toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/credenciales")
    @ResponseStatus(HttpStatus.CREATED)
    CredencialResponse crear(@Valid @RequestBody CrearCredencialRequest s) {
        return CredencialResponse.desde(credenciales.crear(s.proveedor(), s.nombre(), s.token(), s.prioridad()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/credenciales/{id}")
    CredencialResponse editar(@PathVariable UUID id, @Valid @RequestBody EditarCredencialRequest s) {
        return CredencialResponse.desde(credenciales.editar(id, s.nombre(), s.token(), s.prioridad(), s.activo()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/credenciales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(@PathVariable UUID id) {
        credenciales.eliminar(id);
    }
}
