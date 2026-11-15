package pe.estudiodocumental.clientes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import pe.estudiodocumental.PruebaIntegracion;

class ClienteIntegrationTest extends PruebaIntegracion {

    @Test
    void creaPersonaNormalizandoDocumentoYTextos() throws Exception {
        mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":" 4123-4567 ",
                                 "nombres":"  Ana   María ","apellidoPaterno":"Quispe","apellidoMaterno":"Rojas",
                                 "direccion":"Av. Sol 123, Cusco"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDocumento").value("41234567"))
                .andExpect(jsonPath("$.nombres").value("Ana María"))
                .andExpect(jsonPath("$.nombreCompleto").value("Ana María Quispe Rojas"))
                .andExpect(jsonPath("$.origen").value("MANUAL"));
    }

    @Test
    void rechazaDocumentoRepetidoYFormatosInvalidos() throws Exception {
        String token = tokenOperador();
        String persona = """
                {"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"70000001",
                 "nombres":"Luis","apellidoPaterno":"Mamani"}""";
        mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(persona))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(persona))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"123",
                                 "nombres":"X","apellidoPaterno":"Y"}"""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El DNI debe tener 8 dígitos."));

        mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"EMPRESA","tipoDocumento":"DNI","numeroDocumento":"12345678"}"""))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void buscaPorDocumentoYPorNombreYEdita() throws Exception {
        String token = tokenOperador();
        String respuesta = mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"EMPRESA","tipoDocumento":"RUC","numeroDocumento":"20123456789",
                                 "razonSocial":"Inversiones Andinas S.A.C."}"""))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mvc.perform(get("/api/v1/clientes").param("q", "andinas").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].razonSocial").value("Inversiones Andinas S.A.C."));
        mvc.perform(get("/api/v1/clientes/documento/RUC/20123456789").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/clientes/documento/DNI/99999999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/clientes/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"EMPRESA","tipoDocumento":"RUC","numeroDocumento":"20123456789",
                                 "razonSocial":"Inversiones Andinas S.A.C.","direccion":"Jr. Lima 456"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccion").value("Jr. Lima 456"));
    }

    @Test
    void soloElAdminPuedeDarDeBaja() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/clientes")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"70000002",
                                 "nombres":"Rosa","apellidoPaterno":"Huamán"}"""))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mvc.perform(delete("/api/v1/clientes/" + id).header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/clientes/" + id).header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/clientes/documento/DNI/70000002").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isNotFound());
    }
}
