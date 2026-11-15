package pe.estudiodocumental.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import pe.estudiodocumental.DocxDePrueba;
import pe.estudiodocumental.PruebaIntegracion;
import pe.estudiodocumental.importacion.aplicacion.ImportacionV1Service;

class ImportacionV1IntegrationTest extends PruebaIntegracion {

    private static final String CLIENTES = """
            {"format":"document-studio-clients","version":1,"clients":[
              {"kind":"Persona","given":"Rosa Elena","family":"De la Cruz Huamán","documentType":"DNI",
               "number":"46543210","address":"Jr. Ayacucho 45","representatives":"",
               "extrasJson":"{\\"Celular\\":\\"987654321\\",\\"Correo\\":\\"rosa@ejemplo.pe\\"}"},
              {"kind":"Empresa","given":"Comercial Andina SAC","family":"","documentType":"RUC",
               "number":"20512345678","address":"Av. Grau 100","representatives":"","extrasJson":"{}"},
              {"kind":"Persona","given":"Sin","family":"Documento","documentType":"DNI","number":"123",
               "address":"","representatives":"","extrasJson":"{}"}
            ]}""";

    private static String configuracion() {
        String docx = Base64.getEncoder().encodeToString(DocxDePrueba.crear("En {{lugarFirma}}, {{fecha_v1}}."));
        return """
                {"format":"document-studio-config","version":6,
                 "groups":[{"id":1,"name":"Poderes v1","templates":1}],
                 "definitions":[
                   {"id":5,"key":"lugarFirma","label":"Lugar de firma","type":"Texto corto","defaultValue":"Lima"},
                   {"id":6,"key":"fecha_v1","label":"Fecha de firma","type":"Fecha","defaultValue":""}],
                 "apiBindings":[],
                 "templates":[
                   {"id":1,"name":"Carta poder v1","original":"%s","edited":"%s","groupIds":[1],
                    "variables":[{"key":"lugarFirma","definitionId":5},{"key":"fecha_v1","definitionId":6}],
                    "mappings":[],"contactBindings":[],"apiContexts":[]},
                   {"id":2,"name":"Plantilla sin Word","original":"","edited":"","groupIds":[1],
                    "variables":[],"mappings":[],"contactBindings":[],"apiContexts":[]}],
                 "kinds":[]}""".formatted(docx, docx);
    }

    private static byte[] zip(String... nombresYContenidos) throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (int i = 0; i < nombresYContenidos.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nombresYContenidos[i]));
                zip.write(nombresYContenidos[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }

    private MockMultipartFile archivo(byte[] contenido) {
        return new MockMultipartFile("archivo", "exportacion-v1.zip", "application/zip", contenido);
    }

    @Test
    void importaClientesYPlantillasYSePuedeRepetirSinDuplicar() throws Exception {
        String token = tokenAdmin();
        byte[] paquete = zip("clients.json", CLIENTES, "configuration.json", configuracion());

        mvc.perform(multipart("/api/v1/importacion/v1")
                        .file(archivo(paquete))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientesNuevos").value(2))
                .andExpect(jsonPath("$.clientesCompletados").value(0))
                .andExpect(jsonPath("$.clientesOmitidos").value(1))
                .andExpect(jsonPath("$.plantillasCreadas").value(1))
                .andExpect(jsonPath("$.plantillasOmitidas").value(1))
                .andExpect(jsonPath("$.avisos.length()").value(2));

        mvc.perform(get("/api/v1/clientes").param("q", "46543210").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.contenido[0].nombres").value("Rosa Elena"))
                .andExpect(jsonPath("$.contenido[0].apellidoPaterno").value("De la Cruz"))
                .andExpect(jsonPath("$.contenido[0].apellidoMaterno").value("Huamán"))
                .andExpect(jsonPath("$.contenido[0].telefono").value("987654321"))
                .andExpect(jsonPath("$.contenido[0].correo").value("rosa@ejemplo.pe"));
        mvc.perform(get("/api/v1/clientes").param("q", "20512345678").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.contenido[0].razonSocial").value("Comercial Andina SAC"));

        mvc.perform(get("/api/v1/plantillas").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(
                        jsonPath("$[?(@.nombre == 'Carta poder v1')].categoria").value("Poderes v1"));
        mvc.perform(get("/api/v1/variables").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$[?(@.clave == 'lugarfirma')].etiqueta").value("Lugar de firma"))
                .andExpect(
                        jsonPath("$[?(@.clave == 'lugarfirma')].valorDefecto").value("Lima"))
                .andExpect(jsonPath("$[?(@.clave == 'fecha_v1')].tipo").value("FECHA"));

        // Segunda vez: nada se duplica
        mvc.perform(multipart("/api/v1/importacion/v1")
                        .file(archivo(paquete))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientesNuevos").value(0))
                .andExpect(jsonPath("$.clientesCompletados").value(2))
                .andExpect(jsonPath("$.plantillasCreadas").value(0))
                .andExpect(jsonPath("$.plantillasOmitidas").value(2));
    }

    @Test
    void rechazaArchivosQueNoSonDeLaV1() throws Exception {
        String token = tokenAdmin();
        mvc.perform(multipart("/api/v1/importacion/v1")
                        .file(archivo("no es un zip".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(multipart("/api/v1/importacion/v1")
                        .file(archivo(zip("otro.txt", "hola")))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("clients.json")));
        mvc.perform(multipart("/api/v1/importacion/v1")
                        .file(archivo(zip("clients.json", "{\"format\":\"otro\"}")))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void soloElAdministradorPuedeImportar() throws Exception {
        mvc.perform(multipart("/api/v1/importacion/v1")
                        .file(archivo(zip("clients.json", CLIENTES)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenOperador())))
                .andExpect(status().isForbidden());
    }

    @Test
    void separaApellidosConParticulas() {
        assertThat(ImportacionV1Service.separarApellidos("Quispe Rojas")).containsExactly("Quispe", "Rojas");
        assertThat(ImportacionV1Service.separarApellidos("De la Cruz Huamán")).containsExactly("De la Cruz", "Huamán");
        assertThat(ImportacionV1Service.separarApellidos("Mamani")).containsExactly("Mamani", null);
        assertThat(ImportacionV1Service.separarApellidos("Del Río De los Santos"))
                .containsExactly("Del Río", "De los Santos");
    }
}
