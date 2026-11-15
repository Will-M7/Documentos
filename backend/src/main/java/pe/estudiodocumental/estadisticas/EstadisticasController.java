package pe.estudiodocumental.estadisticas;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Estadísticas")
@RestController
@RequestMapping("/api/v1/estadisticas")
class EstadisticasController {

    private final EstadisticasService estadisticas;

    EstadisticasController(EstadisticasService estadisticas) {
        this.estadisticas = estadisticas;
    }

    @Operation(summary = "Resumen del panel de inicio")
    @GetMapping
    EstadisticasService.Resumen resumen() {
        return estadisticas.resumen();
    }
}
