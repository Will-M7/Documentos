package pe.estudiodocumental.consultas.infraestructura;

import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.estudiodocumental.consultas.dominio.Proveedor;
import tools.jackson.databind.JsonNode;

/**
 * Factiliza: {@code GET {url}/dni/info/{numero}} y {@code /ruc/info/{numero}} con {@code Authorization: Bearer}.
 * Responde {@code {success, message, data: {...}}}.
 */
@Component
class FactilizaProveedor implements ProveedorConsulta {

    private final String url;

    FactilizaProveedor(@Value("${app.consultas.factiliza-url:https://api.factiliza.com/v1}") String url) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public Proveedor proveedor() {
        return Proveedor.FACTILIZA;
    }

    @Override
    public DatosConsultados consultar(String tipo, String numero, String token) {
        URI uri = URI.create("%s/%s/info/%s".formatted(url, tipo.toLowerCase(), numero));
        JsonNode raiz = ClienteHttp.obtener(uri, Map.of("Authorization", "Bearer " + token));
        if (!raiz.path("success").asBoolean(false)) {
            throw new FalloConsulta(Motivo.NO_ENCONTRADO, ClienteHttp.texto(raiz, "message"));
        }
        JsonNode d = raiz.path("data");
        String direccion = primero(ClienteHttp.texto(d, "direccion_completa"), ClienteHttp.texto(d, "direccion"));
        DatosConsultados datos = "DNI".equals(tipo)
                ? new DatosConsultados(
                        numero,
                        ClienteHttp.texto(d, "nombres"),
                        ClienteHttp.texto(d, "apellido_paterno"),
                        ClienteHttp.texto(d, "apellido_materno"),
                        null,
                        direccion)
                : new DatosConsultados(
                        numero, null, null, null, ClienteHttp.texto(d, "nombre_o_razon_social"), direccion);
        if (datos.nombres() == null && datos.razonSocial() == null) {
            throw new FalloConsulta(Motivo.RESPUESTA_INVALIDA, "(sin nombre)");
        }
        return datos;
    }

    private static String primero(String a, String b) {
        return a != null ? a : b;
    }
}
