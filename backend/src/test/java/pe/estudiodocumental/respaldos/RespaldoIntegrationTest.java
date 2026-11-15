package pe.estudiodocumental.respaldos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import pe.estudiodocumental.PruebaIntegracion;

class RespaldoIntegrationTest extends PruebaIntegracion {

    @Test
    void sinConfigurarLoIndicaYNoPermiteRespaldar() throws Exception {
        String token = tokenAdmin();
        mvc.perform(get("/api/v1/respaldos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configurado").value(false))
                .andExpect(jsonPath("$.puedeRespaldarAhora").value(false));
        mvc.perform(post("/api/v1/respaldos").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void elOperadorNoVeLosRespaldos() throws Exception {
        mvc.perform(get("/api/v1/respaldos").header(HttpHeaders.AUTHORIZATION, bearer(tokenOperador())))
                .andExpect(status().isForbidden());
    }
}
