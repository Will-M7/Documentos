package pe.estudiodocumental.identidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pe.estudiodocumental.PostgresDePrueba;

/** Login, token y rutas protegidas contra un PostgreSQL real con las migraciones aplicadas. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresDePrueba.class)
class AutenticacionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void loginCorrectoDevuelveTokenYDatosDelUsuario() throws Exception {
        mvc.perform(login("  ADMIN ", "clave-de-prueba-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.usuario.usuario").value("admin"))
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN"));
    }

    @Test
    void claveIncorrectaDevuelve401SinRevelarSiElUsuarioExiste() throws Exception {
        mvc.perform(login("admin", "otra-clave"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos."));
        mvc.perform(login("no-existe", "otra-clave"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void datosVaciosDevuelven400ConErroresPorCampo() throws Exception {
        mvc.perform(login("", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(2));
    }

    @Test
    void rutaProtegidaExigeToken() throws Exception {
        mvc.perform(get("/api/v1/auth/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    void conTokenSeObtieneElUsuarioActual() throws Exception {
        String respuesta = mvc.perform(login("admin", "clave-de-prueba-123"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(respuesta, "$.token");

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Admin de Prueba"));
    }

    @Test
    void saludYDocumentacionSonPublicas() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/docs/openapi.json")).andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String usuario, String clave) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"%s\",\"clave\":\"%s\"}".formatted(usuario, clave));
    }
}
