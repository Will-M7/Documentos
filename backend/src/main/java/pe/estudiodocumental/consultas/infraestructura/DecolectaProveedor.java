package pe.estudiodocumental.consultas.infraestructura;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.estudiodocumental.consultas.dominio.Proveedor;
import tools.jackson.databind.JsonNode;

/**
 * Decolecta (antes apis.net.pe), con plan gratuito de 100 consultas al mes: https://decolecta.gitbook.io/docs.
 *
 * <ul>
 *   <li>DNI: {@code GET {url}/reniec/dni?numero=...} → {@code first_name, first_last_name, second_last_name}.
 *   <li>RUC: {@code GET {url}/sunat/ruc?numero=...} → {@code razon_social, direccion, distrito, provincia,
 *       departamento}.
 * </ul>
 *
 * Token en {@code Authorization: Bearer}.
 */
@Component
class DecolectaProveedor implements ProveedorConsulta {

    private final String url;

    DecolectaProveedor(@Value("${app.consultas.decolecta-url:https://api.decolecta.com/v1}") String url) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public Proveedor proveedor() {
        return Proveedor.DECOLECTA;
    }

    @Override
    public DatosConsultados consultar(String tipo, String numero, String token) {
        String ruta = "DNI".equals(tipo) ? "reniec/dni" : "sunat/ruc";
        URI uri = URI.create("%s/%s?numero=%s".formatted(url, ruta, numero));
        JsonNode d = ClienteHttp.obtener(uri, Map.of("Authorization", "Bearer " + token));
        DatosConsultados datos = "DNI".equals(tipo)
                ? new DatosConsultados(
                        numero,
                        ClienteHttp.texto(d, "first_name"),
                        ClienteHttp.texto(d, "first_last_name"),
                        ClienteHttp.texto(d, "second_last_name"),
                        null,
                        null)
                : new DatosConsultados(numero, null, null, null, ClienteHttp.texto(d, "razon_social"), direccion(d));
        if (datos.nombres() == null && datos.razonSocial() == null) {
            throw new FalloConsulta(Motivo.NO_ENCONTRADO, null);
        }
        return datos;
    }

    /** "AV. SOL 123, CUSCO, CUSCO, CUSCO" a partir de la dirección y el ubigeo. */
    private static String direccion(JsonNode d) {
        String texto = Stream.of(
                        ClienteHttp.texto(d, "direccion"),
                        ClienteHttp.texto(d, "distrito"),
                        ClienteHttp.texto(d, "provincia"),
                        ClienteHttp.texto(d, "departamento"))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return texto.isEmpty() ? null : texto;
    }
}
