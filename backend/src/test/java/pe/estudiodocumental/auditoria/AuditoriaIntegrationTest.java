package pe.estudiodocumental.auditoria;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.estudiodocumental.PruebaIntegracion;

class AuditoriaIntegrationTest extends PruebaIntegracion {

    @Test
    void registraQuienHizoQueIncluidosLosIngresosFallidos() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"operador\",\"clave\":\"incorrecta-123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"46000077",
                                 "nombres":"Elena","apellidoPaterno":"Ccori"}"""))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/auditoria").param("q", "46000077").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].accion").value("CLIENTE_CREADO"))
                .andExpect(jsonPath("$.contenido[0].descripcion").value("Registró un cliente"))
                .andExpect(jsonPath("$.contenido[0].usuario").value("Operador de Prueba"));

        mvc.perform(get("/api/v1/auditoria")
                        .param("q", "INGRESO_FALLIDO")
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(jsonPath("$.contenido[*].detalle", Matchers.hasItem("operador")));
    }

    @Test
    void soloElAdminVeLaAuditoria() throws Exception {
        mvc.perform(get("/api/v1/auditoria").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isForbidden());
    }
}
