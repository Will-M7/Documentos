package pe.estudiodocumental.identidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;
import pe.estudiodocumental.PruebaIntegracion;

/** Renovación del acceso con el token de sesión, rotación, cierre y revocación. */
class SesionIntegrationTest extends PruebaIntegracion {

    @Test
    void renuevaYRotaElTokenDeSesion() throws Exception {
        String login = mvc.perform(login("admin", CLAVE_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenSesion").isNotEmpty())
                .andExpect(jsonPath("$.sesionExpiraEn").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String sesion1 = JsonPath.read(login, "$.tokenSesion");

        String renovado = mvc.perform(renovar(sesion1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.usuario").value("admin"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String acceso = JsonPath.read(renovado, "$.token");
        String sesion2 = JsonPath.read(renovado, "$.tokenSesion");

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", bearer(acceso)))
                .andExpect(status().isOk());

        // Reusar el token viejo (posible robo) cierra también la sesión nueva
        mvc.perform(renovar(sesion1)).andExpect(status().isUnauthorized());
        mvc.perform(renovar(sesion2)).andExpect(status().isUnauthorized());
    }

    @Test
    void cerrarSesionInvalidaElToken() throws Exception {
        String sesion = JsonPath.read(
                mvc.perform(login("admin", CLAVE_ADMIN))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.tokenSesion");
        mvc.perform(post("/api/v1/auth/salir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tokenSesion\":\"%s\"}".formatted(sesion)))
                .andExpect(status().isNoContent());
        mvc.perform(renovar(sesion)).andExpect(status().isUnauthorized());
        mvc.perform(renovar("token-inventado")).andExpect(status().isUnauthorized());
    }

    @Test
    void desactivarAlUsuarioCierraSusSesiones() throws Exception {
        String usuario = "s" + UUID.randomUUID().toString().substring(0, 8);
        String id = JsonPath.read(
                mvc.perform(post("/api/v1/usuarios")
                                .header("Authorization", bearer(tokenAdmin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"usuario":"%s","nombre":"Sesión","clave":"clave-sesion-1","rol":"OPERADOR"}""".formatted(usuario)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        String sesion = JsonPath.read(
                mvc.perform(login(usuario, "clave-sesion-1"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.tokenSesion");

        mvc.perform(put("/api/v1/usuarios/" + id)
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sesión\",\"rol\":\"OPERADOR\",\"activo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(renovar(sesion)).andExpect(status().isUnauthorized());
    }

    private static RequestBuilder login(String usuario, String clave) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"%s\",\"clave\":\"%s\"}".formatted(usuario, clave));
    }

    private static RequestBuilder renovar(String tokenSesion) {
        return post("/api/v1/auth/renovar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenSesion\":\"%s\"}".formatted(tokenSesion));
    }
}
