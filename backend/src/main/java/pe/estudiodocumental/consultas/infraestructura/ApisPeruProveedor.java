package pe.estudiodocumental.consultas.infraestructura;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.estudiodocumental.consultas.dominio.Proveedor;
import tools.jackson.databind.JsonNode;

/**
 * APIsPERU (https://dniruc.apisperu.com/doc/swagger): {@code GET {url}/dni/{numero}?token=...} y {@code /ruc/...}.
 * DNI responde {@code {dni, nombres, apellidoPaterno, apellidoMaterno}}; RUC, {@code {ruc, razonSocial, direccion}}.
 */
@Component
class ApisPeruProveedor implements ProveedorConsulta {

    private final String url;

    ApisPeruProveedor(@Value("${app.consultas.apisperu-url:https://dniruc.apisperu.com/api/v1}") String url) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public Proveedor proveedor() {
        return Proveedor.APISPERU;
    }

    @Override
    public DatosConsultados consultar(String tipo, String numero, String token) {
        URI uri = URI.create("%s/%s/%s?token=%s"
                .formatted(url, tipo.toLowerCase(), numero, URLEncoder.encode(token, StandardCharsets.UTF_8)));
        JsonNode raiz = ClienteHttp.obtener(uri, Map.of());
        if (raiz.has("success") && !raiz.path("success").asBoolean(true)) {
            throw new FalloConsulta(Motivo.NO_ENCONTRADO, ClienteHttp.texto(raiz, "message"));
        }
        DatosConsultados datos = "DNI".equals(tipo)
                ? new DatosConsultados(
                        numero,
                        ClienteHttp.texto(raiz, "nombres"),
                        ClienteHttp.texto(raiz, "apellidoPaterno"),
                        ClienteHttp.texto(raiz, "apellidoMaterno"),
                        null,
                        null)
                : new DatosConsultados(
                        numero,
                        null,
                        null,
                        null,
                        ClienteHttp.texto(raiz, "razonSocial"),
                        ClienteHttp.texto(raiz, "direccion"));
        if (datos.nombres() == null && datos.razonSocial() == null) {
            throw new FalloConsulta(Motivo.NO_ENCONTRADO, null);
        }
        return datos;
    }
}
