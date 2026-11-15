package pe.estudiodocumental.estadisticas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import pe.estudiodocumental.PruebaIntegracion;

class EstadisticasIntegrationTest extends PruebaIntegracion {

    @Test
    void devuelveLosUltimosSeisMesesTerminandoEnElActual() throws Exception {
        String mesActual = YearMonth.now(ZoneId.of("America/Lima")).toString();
        mvc.perform(get("/api/v1/estadisticas").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentosPorMes.length()").value(6))
                .andExpect(jsonPath("$.documentosPorMes[5].nombre").value(mesActual))
                .andExpect(jsonPath("$.documentosMes").isNumber())
                .andExpect(jsonPath("$.clientesTotal").isNumber())
                .andExpect(jsonPath("$.plantillasDelMes").isArray());
    }

    @Test
    void exigeSesion() throws Exception {
        mvc.perform(get("/api/v1/estadisticas")).andExpect(status().isUnauthorized());
    }
}
