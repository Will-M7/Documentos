package pe.estudiodocumental.respaldos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Respaldos")
@RestController
@RequestMapping("/api/v1/respaldos")
@PreAuthorize("hasRole('ADMIN')")
class RespaldoController {

    private final RespaldoService respaldos;

    RespaldoController(RespaldoService respaldos) {
        this.respaldos = respaldos;
    }

    @Operation(
            summary = "Estado de los respaldos",
            description = "Últimos respaldos y si hay uno reciente. Solo ADMIN.")
    @GetMapping
    RespaldoService.Estado estado() {
        return respaldos.estado();
    }

    @Operation(summary = "Respaldar ahora", description = "Ejecuta respaldo.cmd y espera a que termine. Solo ADMIN.")
    @PostMapping
    RespaldoService.Ejecucion respaldarAhora() {
        return respaldos.respaldarAhora();
    }
}
