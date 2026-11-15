package pe.estudiodocumental.solicitudes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** El cliente pide desde el portal (sin usuario), la oficina atiende o rechaza, el cliente consulta el estado. */
class SolicitudIntegrationTest extends PruebaIntegracion {

    private String plantillaId;
    private String nombre;

    @BeforeEach
    void plantillaPublica() throws Exception {
        nombre = "Declaración jurada " + UUID.randomUUID();
        byte[] docx = DocxDePrueba.crear("Yo, {{otorgante.nombre_completo}}, declaro {{motivo}} en {{lugar}}.");
        String r = mvc.perform(multipart("/api/v1/plantillas")
                        .file(new MockMultipartFile("archivo", "dj.docx", "application/octet-stream", docx))
                        .param("nombre", nombre)
                        .header("Authorization", bearer(tokenAdmin())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        plantillaId = JsonPath.read(r, "$.id");
        mvc.perform(put("/api/v1/plantillas/" + plantillaId)
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","activo":true,"publica":true,"participantes":[
                                 {"clave":"otorgante","etiqueta":"Otorgante","tipoCliente":"PERSONA"}]}""".formatted(nombre)))
                .andExpect(status().isOk());
    }

    @Test
    void elClientePideLaOficinaAtiendeYElClienteVeQueEstaListo() throws Exception {
        mvc.perform(get("/api/v1/publico/plantillas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", Matchers.hasItem(nombre)));

        String creada = mvc.perform(post("/api/v1/publico/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("45200001", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo", Matchers.matchesPattern("S-[2-9A-Z]{6}")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String codigo = JsonPath.read(creada, "$.codigo");

        // Todavía no se registró el cliente: lo hará la oficina al generar
        mvc.perform(get("/api/v1/clientes/documento/DNI/45200001").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/publico/solicitudes/" + codigo.toLowerCase()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.contactoNombre").doesNotExist());

        String lista = mvc.perform(get("/api/v1/solicitudes")
                        .param("estado", "PENDIENTE")
                        .header("Authorization", bearer(tokenOperador())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.<java.util.List<String>>read(lista, "$.contenido[?(@.codigo == '" + codigo + "')].id")
                .get(0);
        mvc.perform(get("/api/v1/solicitudes/" + id).header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.datos.variables.motivo").value("no tener antecedentes"))
                .andExpect(jsonPath("$.datos.participantes.otorgante.nombres").value("Julia"));

        mvc.perform(post("/api/v1/documentos")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plantillaId":"%s","solicitudId":"%s","variables":{"motivo":"no tener antecedentes","lugar":"Cusco"},
                                 "participantes":{"otorgante":{"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"45200001",
                                 "nombres":"Julia","apellidoPaterno":"Ccahuana"}}}""".formatted(plantillaId, id)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/publico/solicitudes/" + codigo))
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.mensaje", Matchers.containsString("listo")));
    }

    @Test
    void validaLosDatosYExigeAutorizacion() throws Exception {
        mvc.perform(post("/api/v1/publico/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("45200002", false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", Matchers.containsString("autorizar")));
        mvc.perform(post("/api/v1/publico/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("123", true)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", Matchers.containsString("8 dígitos")));
    }

    @Test
    void laOficinaRechazaYElClienteVeElMotivo() throws Exception {
        String codigo = JsonPath.read(
                mvc.perform(post("/api/v1/publico/solicitudes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(solicitud("45200003", true)))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.codigo");
        String lista = mvc.perform(get("/api/v1/solicitudes").header("Authorization", bearer(tokenOperador())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.<java.util.List<String>>read(lista, "$.contenido[?(@.codigo == '" + codigo + "')].id")
                .get(0);

        mvc.perform(post("/api/v1/solicitudes/" + id + "/rechazar")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Falta la copia del DNI\"}"))
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
        mvc.perform(get("/api/v1/publico/solicitudes/" + codigo))
                .andExpect(jsonPath("$.mensaje").value("No se pudo atender: Falta la copia del DNI"));
    }

    @Test
    void noSeGeneraParaUnaSolicitudRechazadaNiDeOtraPlantilla() throws Exception {
        String id = idDe(enviar(solicitud("45200004", true)));
        String generar = """
                {"plantillaId":"%s","solicitudId":"%s","variables":{"motivo":"x","lugar":"Cusco"},
                 "participantes":{"otorgante":{"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"45200004",
                 "nombres":"Julia","apellidoPaterno":"Ccahuana"}}}""";

        // Otra plantilla: se rechaza antes de generar nada
        String otra = JsonPath.read(
                mvc.perform(multipart("/api/v1/plantillas")
                                .file(new MockMultipartFile(
                                        "archivo",
                                        "otra.docx",
                                        "application/octet-stream",
                                        DocxDePrueba.crear("{{motivo}} {{lugar}}")))
                                .param("nombre", "Otra " + UUID.randomUUID())
                                .header("Authorization", bearer(tokenAdmin())))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");
        mvc.perform(post("/api/v1/documentos")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generar.formatted(otra, id)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", Matchers.containsString("otro documento")));

        mvc.perform(post("/api/v1/solicitudes/" + id + "/rechazar")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Datos incompletos\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/documentos")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generar.formatted(plantillaId, id)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", Matchers.containsString("rechazada")));
        mvc.perform(get("/api/v1/solicitudes/" + id).header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.resumen.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.resumen.documentoId").doesNotExist());
    }

    @Test
    void soloGuardaLosDatosQueLaPlantillaUsa() throws Exception {
        String id = idDe(enviar("""
                {"plantillaId":"%s","variables":{"motivo":"m","lugar":"Cusco","inventada":"basura"},
                 "participantes":{"otorgante":{"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"45200005",
                   "nombres":"Julia","apellidoPaterno":"Ccahuana"},
                   "intruso":{"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"45200006",
                   "nombres":"X","apellidoPaterno":"Y"}},
                 "contactoNombre":"Julia","contactoTelefono":"984000111","aceptaDatos":true}""".formatted(plantillaId)));
        mvc.perform(get("/api/v1/solicitudes/" + id).header("Authorization", bearer(tokenOperador())))
                .andExpect(jsonPath("$.datos.variables.motivo").value("m"))
                .andExpect(jsonPath("$.datos.variables.inventada").doesNotExist())
                .andExpect(jsonPath("$.datos.participantes.intruso").doesNotExist());
    }

    private String enviar(String cuerpo) throws Exception {
        return JsonPath.read(
                mvc.perform(post("/api/v1/publico/solicitudes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.codigo");
    }

    private String idDe(String codigo) throws Exception {
        String lista = mvc.perform(get("/api/v1/solicitudes")
                        .param("tamano", "100")
                        .header("Authorization", bearer(tokenOperador())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.<java.util.List<String>>read(lista, "$.contenido[?(@.codigo == '" + codigo + "')].id")
                .get(0);
    }

    @Test
    void laBandejaNoEsPublica() throws Exception {
        mvc.perform(get("/api/v1/solicitudes")).andExpect(status().isUnauthorized());
    }

    private String solicitud(String dni, boolean acepta) {
        return """
                {"plantillaId":"%s","variables":{"motivo":"no tener antecedentes","lugar":"Cusco"},
                 "participantes":{"otorgante":{"tipo":"PERSONA","tipoDocumento":"DNI","numeroDocumento":"%s",
                   "nombres":"Julia","apellidoPaterno":"Ccahuana"}},
                 "contactoNombre":"Julia Ccahuana","contactoTelefono":"984000111","aceptaDatos":%s}""".formatted(plantillaId, dni, acepta);
    }
}
