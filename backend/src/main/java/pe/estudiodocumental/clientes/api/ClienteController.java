package pe.estudiodocumental.clientes.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import pe.estudiodocumental.clientes.api.dto.ClienteRequest;
import pe.estudiodocumental.clientes.api.dto.ClienteResponse;
import pe.estudiodocumental.clientes.aplicacion.ClienteService;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;
import pe.estudiodocumental.comun.api.dto.Pagina;

@Tag(name = "Clientes")
@RestController
@RequestMapping("/api/v1/clientes")
class ClienteController {

    private final ClienteService clientes;

    ClienteController(ClienteService clientes) {
        this.clientes = clientes;
    }

    @Operation(summary = "Buscar clientes", description = "Por número de documento (inicio) o por nombre (contiene).")
    @GetMapping
    Pagina<ClienteResponse> buscar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return Pagina.de(clientes.buscar(q, pagina, tamano), ClienteResponse::desde);
    }

    @Operation(summary = "Buscar por documento exacto", description = "404 si no existe en la base local.")
    @GetMapping("/documento/{tipo}/{numero}")
    ResponseEntity<ClienteResponse> porDocumento(@PathVariable TipoDocumento tipo, @PathVariable String numero) {
        return ResponseEntity.of(clientes.buscarPorDocumento(tipo, numero).map(ClienteResponse::desde));
    }

    @GetMapping("/{id}")
    ClienteResponse obtener(@PathVariable UUID id) {
        return ClienteResponse.desde(clientes.obtener(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClienteResponse crear(@Valid @RequestBody ClienteRequest solicitud) {
        return ClienteResponse.desde(clientes.crear(solicitud.aDatos()));
    }

    @PutMapping("/{id}")
    ClienteResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ClienteRequest solicitud) {
        return ClienteResponse.desde(clientes.actualizar(id, solicitud.aDatos()));
    }

    @Operation(summary = "Dar de baja", description = "Solo ADMIN. No se borra: se oculta y conserva el historial.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desactivar(@PathVariable UUID id) {
        clientes.desactivar(id);
    }
}
