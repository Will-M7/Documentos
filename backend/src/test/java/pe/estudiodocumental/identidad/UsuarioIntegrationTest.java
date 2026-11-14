package pe.estudiodocumental.identidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import pe.estudiodocumental.PruebaIntegracion;

class UsuarioIntegrationTest extends PruebaIntegracion {

    @Test
    void elAdminCreaEditaYRestableceLaClave() throws Exception {
        String usuario = "u" + UUID.randomUUID().toString().substring(0, 8);
        String respuesta = mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario":"%s","nombre":"María Torres","clave":"clave-inicial-1","rol":"OPERADOR"}""".formatted(usuario)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("OPERADOR"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mvc.perform(login(usuario, "clave-inicial-1")).andExpect(status().isOk());

        mvc.perform(put("/api/v1/usuarios/" + id + "/clave")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"otra-clave-22\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(login(usuario, "clave-inicial-1")).andExpect(status().isUnauthorized());
        mvc.perform(login(usuario, "otra-clave-22")).andExpect(status().isOk());

        mvc.perform(put("/api/v1/usuarios/" + id)
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"María Torres\",\"rol\":\"OPERADOR\",\"activo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(login(usuario, "otra-clave-22")).andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaClavesCortasYUsuariosRepetidos() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"nuevo\",\"nombre\":\"X\",\"clave\":\"corta\",\"rol\":\"OPERADOR\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", Matchers.containsString("al menos 10")));
        mvc.perform(
                        post("/api/v1/usuarios")
                                .header("Authorization", bearer(tokenAdmin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"usuario\":\"ADMIN\",\"nombre\":\"X\",\"clave\":\"clave-larga-1\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unAdminNoPuedeDesactivarseNiElOperadorAdministrar() throws Exception {
        String yo = JsonPath.read(
                mvc.perform(get("/api/v1/auth/yo").header("Authorization", bearer(tokenAdmin())))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        mvc.perform(put("/api/v1/usuarios/" + yo)
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Admin\",\"rol\":\"OPERADOR\",\"activo\":true}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/usuarios").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isForbidden());
    }

    @Test
    void cincoIntentosFallidosBloqueanYElAdminDesbloquea() throws Exception {
        String usuario = "b" + UUID.randomUUID().toString().substring(0, 8);
        String id = JsonPath.read(
                mvc.perform(post("/api/v1/usuarios")
                                .header("Authorization", bearer(tokenAdmin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"usuario":"%s","nombre":"Bloqueo","clave":"clave-buena-1","rol":"OPERADOR"}""".formatted(usuario)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        for (int i = 0; i < 5; i++) {
            mvc.perform(login(usuario, "clave-mala-123")).andExpect(status().isUnauthorized());
        }
        mvc.perform(login(usuario, "clave-buena-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", Matchers.containsString("bloqueado")));

        mvc.perform(put("/api/v1/usuarios/" + id + "/clave")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"clave-nueva-123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(login(usuario, "clave-nueva-123")).andExpect(status().isOk());
    }

    @Test
    void cadaUsuarioCambiaSuPropiaClave() throws Exception {
        String usuario = "c" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/usuarios")
                .header("Authorization", bearer(tokenAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"usuario":"%s","nombre":"Cambio","clave":"clave-propia-1","rol":"OPERADOR"}""".formatted(usuario)));
        String token = JsonPath.read(
                mvc.perform(login(usuario, "clave-propia-1"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.token");
        mvc.perform(put("/api/v1/auth/clave")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"equivocada\",\"nueva\":\"clave-propia-2\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/v1/auth/clave")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actual\":\"clave-propia-1\",\"nueva\":\"clave-propia-2\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(login(usuario, "clave-propia-2")).andExpect(status().isOk());
    }

    private static RequestBuilder login(String usuario, String clave) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"%s\",\"clave\":\"%s\"}".formatted(usuario, clave));
    }
}
