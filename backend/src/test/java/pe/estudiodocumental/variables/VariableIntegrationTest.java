package pe.estudiodocumental.variables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.estudiodocumental.PruebaIntegracion;
import pe.estudiodocumental.variables.aplicacion.VariableService;
import pe.estudiodocumental.variables.dominio.TipoVariable;

class VariableIntegrationTest extends PruebaIntegracion {

    @Autowired
    VariableService servicio;

    @Test
    void elCatalogoTraeVariablesIniciales() throws Exception {
        mvc.perform(get("/api/v1/variables").header("Authorization", bearer(tokenOperador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.clave == 'fecha')].tipo").value("FECHA"));
    }

    @Test
    void elAdminCreaYEditaPeroLaClaveNoCambia() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/variables")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clave":" Numero_Partida ","etiqueta":"Número de partida","tipo":"TEXTO"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clave").value("numero_partida"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mvc.perform(put("/api/v1/variables/" + id)
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"etiqueta":"N.º de partida registral","tipo":"TEXTO","valorDefecto":"-"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clave").value("numero_partida"))
                .andExpect(jsonPath("$.etiqueta").value("N.º de partida registral"));

        mvc.perform(post("/api/v1/variables")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clave":"numero_partida","etiqueta":"Otra","tipo":"TEXTO"}"""))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/variables")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clave":"1-mala","etiqueta":"Mala","tipo":"TEXTO"}"""))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void elOperadorNoPuedeCrear() throws Exception {
        mvc.perform(post("/api/v1/variables")
                        .header("Authorization", bearer(tokenOperador()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clave":"algo","etiqueta":"Algo","tipo":"TEXTO"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void asegurarCreaSoloLasQueFaltan() {
        var resultado = servicio.asegurar(List.of("fecha", "nombre_del_bien"));
        assertThat(resultado.get("fecha").getTipo()).isEqualTo(TipoVariable.FECHA);
        assertThat(resultado.get("nombre_del_bien").getEtiqueta()).isEqualTo("Nombre del bien");
        assertThat(servicio.asegurar(List.of("nombre_del_bien"))
                        .get("nombre_del_bien")
                        .getId())
                .isEqualTo(resultado.get("nombre_del_bien").getId());
    }
}
