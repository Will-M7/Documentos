package pe.estudiodocumental.consultas.infraestructura;

import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import pe.estudiodocumental.consultas.infraestructura.ProveedorConsulta.FalloConsulta;
import pe.estudiodocumental.consultas.infraestructura.ProveedorConsulta.Motivo;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** GET con tiempo límite que convierte errores HTTP y de red en {@link FalloConsulta}. */
final class ClienteHttp {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ClienteHttp() {}

    static JsonNode obtener(URI uri, Map<String, String> cabeceras) {
        HttpRequest.Builder pedido =
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET();
        cabeceras.forEach(pedido::header);
        HttpResponse<String> respuesta;
        try {
            respuesta = HTTP.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException | ConnectException | UnknownHostException e) {
            throw new FalloConsulta(Motivo.SIN_CONEXION, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalloConsulta(Motivo.SIN_CONEXION, null);
        } catch (Exception e) {
            throw new FalloConsulta(Motivo.SIN_CONEXION, e.getMessage());
        }
        int estado = respuesta.statusCode();
        switch (estado) {
            case 401, 403 -> throw new FalloConsulta(Motivo.CREDENCIAL_RECHAZADA, null);
            case 404, 422 -> throw new FalloConsulta(Motivo.NO_ENCONTRADO, null);
            case 429 -> throw new FalloConsulta(Motivo.LIMITE, null);
            default -> {
                if (estado >= 500) {
                    throw new FalloConsulta(Motivo.SIN_CONEXION, "(error " + estado + " del proveedor)");
                }
                if (estado != 200) {
                    throw new FalloConsulta(Motivo.RESPUESTA_INVALIDA, "(código " + estado + ")");
                }
            }
        }
        try {
            return JSON.readTree(respuesta.body());
        } catch (Exception e) {
            throw new FalloConsulta(Motivo.RESPUESTA_INVALIDA, null);
        }
    }

    /** Texto del campo, o null si falta o está vacío. */
    static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        String texto = valor.isMissingNode() || valor.isNull() ? null : valor.asString();
        return texto == null || texto.isBlank() || "-".equals(texto.strip()) ? null : texto.strip();
    }
}
