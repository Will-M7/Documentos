package pe.estudiodocumental.consultas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.estudiodocumental.PruebaIntegracion;

/** Consultas contra un Factiliza simulado en un puerto local (respuesta con el formato real del proveedor). */
class ConsultaIntegrationTest extends PruebaIntegracion {

    private static final AtomicInteger LLAMADAS = new AtomicInteger();
    private static final HttpServer FACTILIZA = iniciarFactilizaSimulado();

    @DynamicPropertySource
    static void apuntarAlSimulado(DynamicPropertyRegistry propiedades) {
        propiedades.add(
                "app.consultas.factiliza-url",
                () -> "http://localhost:" + FACTILIZA.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void detener() {
        FACTILIZA.stop(0);
    }

    @AfterEach
    void borrarCredenciales() throws Exception {
        String lista = mvc.perform(get("/api/v1/credenciales").header("Authorization", bearer(tokenAdmin())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (Object id : JsonPath.<java.util.List<Object>>read(lista, "$.credenciales[*].id")) {
            mvc.perform(delete("/api/v1/credenciales/" + id).header("Authorization", bearer(tokenAdmin())));
        }
    }

    @Test
    void sinCredencialesExplicaQueSeCompleteAMano() throws Exception {
        mvc.perform(post("/api/v1/consultas/DNI/45000001").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado").value(false))
                .andExpect(jsonPath("$.mensaje", Matchers.containsString("no hay credenciales")));
    }

    @Test
    void consultaAlProveedorGuardaEnClientesYLaSegundaVezNoGasta() throws Exception {
        mvc.perform(post("/api/v1/credenciales")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proveedor":"FACTILIZA","nombre":"Principal","token":"token-secreto-1234","prioridad":1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenFinal").value("…1234"))
                .andExpect(jsonPath("$.token").doesNotExist());
        int antes = LLAMADAS.get();

        mvc.perform(post("/api/v1/consultas/DNI/45000002").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado").value(true))
                .andExpect(jsonPath("$.origen").value("Factiliza"))
                .andExpect(jsonPath("$.cliente.nombreCompleto").value("Juan Carlos Pérez Soto"))
                .andExpect(jsonPath("$.cliente.direccion").value("AV. EL SOL 100, CUSCO"))
                .andExpect(jsonPath("$.cliente.origen").value("FACTILIZA"));

        mvc.perform(post("/api/v1/consultas/DNI/45000002").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.origen").value("CLIENTES"));
        assertThat(LLAMADAS.get() - antes).isEqualTo(1);

        mvc.perform(post("/api/v1/consultas/DNI/45099999").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.encontrado").value(false))
                .andExpect(jsonPath("$.mensaje", Matchers.containsString("No se encontró")));

        mvc.perform(get("/api/v1/credenciales").header("Authorization", bearer(tokenAdmin())))
                .andExpect(jsonPath("$.consultasDelMes", Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void tokenRechazadoSeInforma() throws Exception {
        mvc.perform(post("/api/v1/credenciales")
                .header("Authorization", bearer(tokenAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"proveedor":"FACTILIZA","token":"token-vencido-9999","prioridad":1}"""));
        mvc.perform(post("/api/v1/consultas/DNI/45000003").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.encontrado").value(false))
                .andExpect(jsonPath("$.mensaje", Matchers.containsString("rechazó el token")));
    }

    @Test
    void validaElFormatoYSoloAdminVeCredenciales() throws Exception {
        mvc.perform(post("/api/v1/consultas/DNI/123").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/credenciales").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isForbidden());
    }

    /** Responde como Factiliza: 401 con token vencido, 404 para 45099999, datos para cualquier otro DNI. */
    private static HttpServer iniciarFactilizaSimulado() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/v1/dni/info/", intercambio -> {
                LLAMADAS.incrementAndGet();
                String dni = intercambio.getRequestURI().getPath().replaceAll(".*/", "");
                String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
                int estado;
                String cuerpo;
                if (autorizacion == null || autorizacion.contains("vencido")) {
                    estado = 401;
                    cuerpo = "{\"success\":false,\"message\":\"Token inválido\"}";
                } else if ("45099999".equals(dni)) {
                    estado = 404;
                    cuerpo = "{\"success\":false,\"message\":\"No encontrado\"}";
                } else {
                    estado = 200;
                    cuerpo = """
                            {"status":200,"success":true,"message":"Exito","data":{"numero":"%s",
                             "nombres":"JUAN CARLOS","apellido_paterno":"PÉREZ","apellido_materno":"SOTO",
                             "nombre_completo":"PÉREZ SOTO, JUAN CARLOS","direccion":"AV. EL SOL 100",
                             "direccion_completa":"AV. EL SOL 100, CUSCO"}}""".formatted(dni)
                            .replace("JUAN CARLOS", "Juan Carlos")
                            .replace("PÉREZ\"", "Pérez\"")
                            .replace("\"SOTO\"", "\"Soto\"");
                }
                byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                intercambio.sendResponseHeaders(estado, bytes.length);
                intercambio.getResponseBody().write(bytes);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
