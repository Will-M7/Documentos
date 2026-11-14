package pe.estudiodocumental.documentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pe.estudiodocumental.DocxDePrueba;
import pe.estudiodocumental.PruebaIntegracion;

class DocumentoIntegrationTest extends PruebaIntegracion {

    private String plantillaId;

    @BeforeEach
    void subirPlantilla() throws Exception {
        byte[] docx = DocxDePrueba.crear(
                "Yo, {{otorgante.nombre_completo | mayusculas}}, identificado con {{otorgante.documento}},",
                "domiciliado en {{otorgante.direccion}}, otorgo poder a {{apoderado.nombre_completo}}",
                "para cobrar {{monto | moneda}} ({{monto | letras}}). {{lugar}}, {{fecha | fecha_larga}}.");
        String respuesta = mvc.perform(multipart("/api/v1/plantillas")
                        .file(new MockMultipartFile("archivo", "poder.docx", "application/octet-stream", docx))
                        .param("nombre", "Poder " + UUID.randomUUID())
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        plantillaId = JsonPath.read(respuesta, "$.id");
    }

    @Test
    void generaElWordGuardaLosClientesYQuedaEnElHistorial() throws Exception {
        String token = tokenOperador();
        String respuesta = mvc.perform(post("/api/v1/documentos")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("41230001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumen.numero").isNumber())
                .andExpect(jsonPath("$.resumen.titulo", Matchers.startsWith("Poder ")))
                .andExpect(jsonPath("$.resumen.usuarioNombre").value("Operador de Prueba"))
                .andExpect(jsonPath("$.resumen.participantes[0].nombre").value("Ana María Quispe Rojas"))
                .andExpect(jsonPath("$.marcas['{{monto | letras}}']").value("MIL QUINIENTOS CON 00/100 SOLES"))
                .andExpect(jsonPath("$.variables.fecha").value("2026-11-14"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(respuesta, "$.resumen.id");

        byte[] docx = mvc.perform(get("/api/v1/documentos/" + id + "/docx").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        String texto = DocxDePrueba.texto(docx);
        assertThat(texto)
                .contains("Yo, ANA MARÍA QUISPE ROJAS, identificado con DNI N.º 41230001,")
                .contains("domiciliado en Av. Sol 123, Cusco, otorgo poder a Luis Mamani")
                .contains("para cobrar S/ 1,500.00 (MIL QUINIENTOS CON 00/100 SOLES). Cusco, 14 de noviembre de 2026.")
                .contains("Firma: Ana María Quispe Rojas")
                .doesNotContain("{{");

        // Los participantes quedaron registrados en Clientes
        mvc.perform(get("/api/v1/clientes/documento/DNI/41230001").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccion").value("Av. Sol 123, Cusco"));

        // Y el documento aparece al buscar por DNI en el historial
        mvc.perform(get("/api/v1/documentos").param("q", "41230001").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(id));
    }

    @Test
    void indicaQueFaltaAntesDeGenerar() throws Exception {
        mvc.perform(post("/api/v1/documentos")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plantillaId":"%s","variables":{"monto":"mil"},"participantes":{}}""".formatted(plantillaId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", Matchers.containsString("no es un número válido")))
                .andExpect(jsonPath("$.detail", Matchers.containsString("Complete «Lugar»")));
    }

    @Test
    void sinLibreOfficeElPdfNoEstaYSeExplicaPorQue() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/documentos")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido("41230002")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        boolean tienePdf = JsonPath.read(respuesta, "$.resumen.tienePdf");
        if (!tienePdf) {
            String id = JsonPath.read(respuesta, "$.resumen.id");
            assertThat((String) JsonPath.read(respuesta, "$.avisoPdf")).contains("LibreOffice");
            mvc.perform(get("/api/v1/documentos/" + id + "/pdf").header("Authorization", bearer(tokenOperador())))
                    .andExpect(status().isNotFound());
        }
    }

    private String pedido(String dni) {
        return """
                {"plantillaId":"%s",
                 "variables":{"monto":"1500","lugar":"Cusco","fecha":"2026-11-14"},
                 "participantes":{
                   "otorgante":{"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"%s",
                                "nombres":"Ana María","apellidoPaterno":"Quispe","apellidoMaterno":"Rojas",
                                "direccion":"Av. Sol 123, Cusco"},
                   "apoderado":{"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"70000099",
                                "nombres":"Luis","apellidoPaterno":"Mamani"}}}""".formatted(plantillaId, dni);
    }
}
