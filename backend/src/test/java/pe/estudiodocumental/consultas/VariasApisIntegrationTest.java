package pe.estudiodocumental.consultas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.estudiodocumental.PruebaIntegracion;

/** Varias APIs con cupo: cuando una se agota (límite mensual o respuesta 429) se usa la siguiente sola. */
class VariasApisIntegrationTest extends PruebaIntegracion {

    private static final HttpServer SIMULADOR = iniciar();

    @DynamicPropertySource
    static void apuntar(DynamicPropertyRegistry p) {
        String base = "http://localhost:" + SIMULADOR.getAddress().getPort();
        p.add("app.consultas.decolecta-url", () -> base + "/decolecta/v1");
        p.add("app.consultas.factiliza-url", () -> base + "/factiliza/v1");
    }

    @AfterAll
    static void detener() {
        SIMULADOR.stop(0);
    }

    @AfterEach
    void borrarCredenciales() throws Exception {
        String lista = mvc.perform(get("/api/v1/credenciales").header("Authorization", bearer(tokenAdmin())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (Object id : JsonPath.<List<Object>>read(lista, "$.credenciales[*].id")) {
            mvc.perform(delete("/api/v1/credenciales/" + id).header("Authorization", bearer(tokenAdmin())));
        }
    }

    @Test
    void decolectaPrimeroYAlAgotarSuCupoMensualPasaAFactiliza() throws Exception {
        credencial("DECOLECTA", "token-decolecta-gratis", 1, 1);
        credencial("FACTILIZA", "token-factiliza-pago", 2, null);

        mvc.perform(post("/api/v1/consultas/DNI/45100001").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.origen").value("Decolecta"))
                .andExpect(jsonPath("$.cliente.nombres").value("Roxana Karina"))
                .andExpect(jsonPath("$.cliente.apellidoPaterno").value("Delgado"))
                .andExpect(jsonPath("$.cliente.origen").value("DECOLECTA"));

        // Decolecta ya usó su única consulta del mes: va directo a Factiliza
        mvc.perform(post("/api/v1/consultas/DNI/45100002").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.origen").value("Factiliza"));

        mvc.perform(get("/api/v1/credenciales").header("Authorization", bearer(tokenAdmin())))
                .andExpect(jsonPath("$.credenciales[0].proveedor").value("DECOLECTA"))
                .andExpect(jsonPath("$.credenciales[0].usadasEsteMes").value(1))
                .andExpect(jsonPath("$.credenciales[0].limiteMensual").value(1));
    }

    @Test
    void siElProveedorDiceQueSeAcaboElCupoSeMarcaAgotadaYSeSigueConLaSiguiente() throws Exception {
        credencial("DECOLECTA", "token-decolecta-agotado", 1, 100);
        credencial("FACTILIZA", "token-factiliza-pago", 2, null);

        mvc.perform(post("/api/v1/consultas/DNI/45100003").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.origen").value("Factiliza"));

        mvc.perform(get("/api/v1/credenciales").header("Authorization", bearer(tokenAdmin())))
                .andExpect(jsonPath("$.credenciales[0].agotadaHasta").isNotEmpty());
    }

    @Test
    void consultaRucConDireccionCompleta() throws Exception {
        credencial("DECOLECTA", "token-decolecta-gratis", 1, null);
        mvc.perform(post("/api/v1/consultas/RUC/20601030013").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.cliente.razonSocial").value("REXTIE S.A.C."))
                .andExpect(jsonPath("$.cliente.direccion", Matchers.startsWith("AV. JOSE GALVEZ BARRENECHEA NRO 566")))
                .andExpect(jsonPath("$.cliente.direccion", Matchers.endsWith("SAN ISIDRO, LIMA, LIMA")));
    }

    @Test
    void sinCupoEnNingunaSeExplicaQueHacer() throws Exception {
        credencial("DECOLECTA", "token-decolecta-gratis", 1, 1);
        mvc.perform(post("/api/v1/consultas/DNI/45100004").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/consultas/DNI/45100005").header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.encontrado").value(false))
                .andExpect(jsonPath("$.mensaje", Matchers.containsString("gastaron su cupo")));
    }

    private void credencial(String proveedor, String token, int prioridad, Integer limite) throws Exception {
        mvc.perform(post("/api/v1/credenciales")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"proveedor":"%s","token":"%s","prioridad":%d,"limiteMensual":%s}""".formatted(proveedor, token, prioridad, limite)))
                .andExpect(status().isCreated());
    }

    /** Responde como Decolecta (formato de su documentación) y como Factiliza. */
    private static HttpServer iniciar() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            s.createContext("/decolecta/v1/reniec/dni", x -> {
                if (cabecera(x).contains("agotado")) {
                    responder(x, 429, "{\"message\":\"Limite alcanzado\"}");
                    return;
                }
                String numero = x.getRequestURI().getQuery().replace("numero=", "");
                responder(x, 200, """
                        {"first_name":"Roxana Karina","first_last_name":"Delgado","second_last_name":"Huamani",
                         "full_name":"DELGADO HUAMANI ROXANA KARINA","document_number":"%s"}""".formatted(numero));
            });
            s.createContext("/decolecta/v1/sunat/ruc", x -> responder(x, 200, """
                    {"razon_social":"REXTIE S.A.C.","numero_documento":"20601030013","estado":"ACTIVO",
                     "condicion":"HABIDO","direccion":"AV. JOSE GALVEZ BARRENECHEA NRO 566 INT. 101 URB. CORPAC ",
                     "distrito":"SAN ISIDRO","provincia":"LIMA","departamento":"LIMA"}"""));
            s.createContext("/factiliza/v1/dni/info/", x -> responder(x, 200, """
                    {"success":true,"data":{"numero":"1","nombres":"Pedro","apellido_paterno":"Soto",
                     "apellido_materno":"Paz","direccion_completa":"JR. LIMA 1"}}"""));
            s.start();
            return s;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cabecera(HttpExchange x) {
        String valor = x.getRequestHeaders().getFirst("Authorization");
        return valor == null ? "" : valor;
    }

    private static void responder(HttpExchange x, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        x.sendResponseHeaders(estado, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }
}
