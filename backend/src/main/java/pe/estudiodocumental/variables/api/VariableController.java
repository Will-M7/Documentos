package pe.estudiodocumental.variables.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.estudiodocumental.variables.api.dto.VariableDtos.CrearVariableRequest;
import pe.estudiodocumental.variables.api.dto.VariableDtos.EditarVariableRequest;
import pe.estudiodocumental.variables.api.dto.VariableDtos.VariableResponse;
import pe.estudiodocumental.variables.aplicacion.VariableService;

@Tag(name = "Variables")
@RestController
@RequestMapping("/api/v1/variables")
class VariableController {

    private final VariableService variables;

    VariableController(VariableService variables) {
        this.variables = variables;
    }

    @GetMapping
    List<VariableResponse> listar() {
        return variables.listar().stream().map(VariableResponse::desde).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VariableResponse crear(@Valid @RequestBody CrearVariableRequest s) {
        return VariableResponse.desde(
                variables.crear(s.clave(), s.etiqueta(), s.tipo(), s.valorDefecto(), s.descripcion()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    VariableResponse editar(@PathVariable UUID id, @Valid @RequestBody EditarVariableRequest s) {
        return VariableResponse.desde(variables.editar(id, s.etiqueta(), s.tipo(), s.valorDefecto(), s.descripcion()));
    }
}
