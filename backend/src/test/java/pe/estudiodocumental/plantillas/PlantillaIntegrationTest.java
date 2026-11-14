package pe.estudiodocumental.plantillas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pe.estudiodocumental.DocxDePrueba;
import pe.estudiodocumental.PruebaIntegracion;

class PlantillaIntegrationTest extends PruebaIntegracion {

    static MockMultipartFile archivo(byte[] docx) {
        return new MockMultipartFile(
                "archivo",
                "carta-poder.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx);
    }

    @Test
    void subeDetectaYVersiona() throws Exception {
        String nombre = "Carta poder " + UUID.randomUUID();
        byte[] v1 = DocxDePrueba.crear(
                "Yo, {{otorgante.nombres}} {{otorgante.apellidos}}, identificado con {{otorgante.documento}},",
                "otorgo poder a {{apoderado.nombre_completo}} para {{motivo}}, en {{lugar}}, {{fecha | fecha_larga}}.");

        String respuesta = mvc.perform(multipart("/api/v1/plantillas")
                        .file(archivo(v1))
                        .param("nombre", nombre)
                        .param("categoria", "Poderes")
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionActual").value(1))
                .andExpect(jsonPath("$.participantes[0].clave").value("otorgante"))
                .andExpect(jsonPath("$.participantes[0].campos[*].clave")
                        .value(Matchers.contains("nombres", "apellidos", "documento", "nombre_completo")))
                .andExpect(jsonPath("$.participantes[1].clave").value("apoderado"))
                .andExpect(jsonPath("$.variables[*].clave").value(Matchers.contains("motivo", "lugar", "fecha")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        // El administrador nombra a los participantes; la configuración sobrevive a una versión nueva
        mvc.perform(put("/api/v1/plantillas/" + id)
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"%s","categoria":"Poderes","activo":true,"participantes":[
                                  {"clave":"otorgante","etiqueta":"Poderdante","tipoCliente":"PERSONA"},
                                  {"clave":"apoderado","etiqueta":"Apoderado","tipoCliente":"PERSONA"}]}""".formatted(nombre)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes[0].etiqueta").value("Poderdante"));

        byte[] v2 = DocxDePrueba.crear("Yo, {{otorgante.nombres}}, en {{lugar}} y con {{nuevo_dato}}.");
        mvc.perform(multipart("/api/v1/plantillas/" + id + "/versiones")
                        .file(archivo(v2))
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionActual").value(2))
                .andExpect(jsonPath("$.participantes.length()").value(1))
                .andExpect(jsonPath("$.participantes[0].etiqueta").value("Poderdante"))
                .andExpect(jsonPath("$.variables[*].clave").value(Matchers.contains("lugar", "nuevo_dato")))
                .andExpect(jsonPath("$.versiones[*].numero").value(Matchers.contains(2, 1)));

        byte[] descargado = mvc.perform(get("/api/v1/plantillas/" + id + "/versiones/1/docx")
                        .header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(descargado).isEqualTo(v1);
    }

    @Test
    void explicaQueCorregirSiLaPlantillaTieneErrores() throws Exception {
        byte[] malo = DocxDePrueba.crear("{{otorgante.dni}} {{monto | dolares}}");
        mvc.perform(multipart("/api/v1/plantillas")
                        .file(archivo(malo))
                        .param("nombre", "Mala " + UUID.randomUUID())
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("'dni' no es un dato de cliente")))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("el formato 'dolares' no existe")));

        mvc.perform(multipart("/api/v1/plantillas")
                        .file(new MockMultipartFile(
                                "archivo", "x.docx", "application/octet-stream", "texto".getBytes()))
                        .param("nombre", "No es Word " + UUID.randomUUID())
                        .header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void elOperadorVePeroNoSube() throws Exception {
        mvc.perform(get("/api/v1/plantillas").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/plantillas")
                        .file(archivo(DocxDePrueba.crear("{{fecha}}")))
                        .param("nombre", "Intento")
                        .header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isForbidden());
    }
}
