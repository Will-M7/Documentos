package pe.estudiodocumental.comun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.estudiodocumental.PruebaIntegracion;

/** Con app.web.directorio, el backend sirve la web y deja la API protegida. */
class WebEstaticaIntegrationTest extends PruebaIntegracion {

    private static final Path WEB = crearWeb();

    @DynamicPropertySource
    static void web(DynamicPropertyRegistry propiedades) {
        propiedades.add("app.web.directorio", WEB::toString);
    }

    @Test
    void sirveLaWebYLasRutasDeReactDevuelvenIndex() throws Exception {
        // MockMvc no ejecuta el reenvío: se verifica a dónde reenvía
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string("console.log('app')"));
        mvc.perform(get("/clientes"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("raiz")));
    }

    @Test
    void laApiSigueProtegida() throws Exception {
        mvc.perform(get("/api/v1/clientes")).andExpect(status().isUnauthorized());
    }

    private static Path crearWeb() {
        try {
            Path dir = Files.createTempDirectory("web-prueba");
            Files.writeString(dir.resolve("index.html"), "<div id=\"raiz\"></div>");
            Files.createDirectories(dir.resolve("assets"));
            Files.writeString(dir.resolve("assets/app.js"), "console.log('app')");
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
