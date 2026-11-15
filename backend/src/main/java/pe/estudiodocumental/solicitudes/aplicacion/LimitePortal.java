package pe.estudiodocumental.solicitudes.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.estudiodocumental.comun.error.LimiteExcedidoException;

/**
 * Límites por dirección IP en el portal público, para que nadie llene la bandeja de la oficina con envíos automáticos
 * ni pruebe códigos al azar. Se guarda en memoria: al reiniciar el sistema se vuelve a empezar.
 *
 * <p>La IP es la de la conexión ({@code getRemoteAddr}); no se confía en {@code X-Forwarded-For}, que cualquiera
 * puede escribir. Si algún día el portal se publica detrás de un proxy, configure el proxy como confiable en Spring
 * ({@code server.forward-headers-strategy}).
 */
@Component
class LimitePortal {

    private static final Duration VENTANA = Duration.ofMinutes(10);

    private final Contador envios;
    private final Contador consultas;

    LimitePortal(
            @Value("${app.portal.solicitudes-por-10-minutos:5}") int maximoEnvios,
            @Value("${app.portal.consultas-por-10-minutos:60}") int maximoConsultas) {
        this.envios = new Contador(
                maximoEnvios,
                "Se enviaron demasiadas solicitudes desde esta conexión. Intente de nuevo en unos minutos.");
        this.consultas = new Contador(
                maximoConsultas,
                "Se hicieron demasiadas consultas desde esta conexión. Intente de nuevo en unos minutos.");
    }

    /** Falla si esta IP ya llegó al límite de envíos, sin contar este intento. */
    void verificarEnvio(String ip) {
        envios.verificar(ip);
    }

    /** Cuenta un envío que sí se guardó. */
    void registrarEnvio(String ip) {
        envios.registrar(ip);
    }

    void registrarConsulta(String ip) {
        consultas.verificar(ip);
        consultas.registrar(ip);
    }

    /** Ventana deslizante por IP. Las IP sin actividad reciente se borran para que el mapa no crezca sin fin. */
    private static final class Contador {
        private final int maximo;
        private final String mensaje;
        private final Map<String, Deque<Instant>> porIp = new ConcurrentHashMap<>();

        Contador(int maximo, String mensaje) {
            this.maximo = maximo;
            this.mensaje = mensaje;
        }

        void verificar(String ip) {
            Instant ahora = Instant.now();
            Deque<Instant> lista = porIp.get(clave(ip));
            if (lista == null) {
                return;
            }
            synchronized (lista) {
                limpiar(lista, ahora);
                if (lista.size() >= maximo) {
                    throw new LimiteExcedidoException(mensaje);
                }
            }
        }

        void registrar(String ip) {
            Instant ahora = Instant.now();
            Deque<Instant> lista = porIp.computeIfAbsent(clave(ip), k -> new ArrayDeque<>());
            synchronized (lista) {
                limpiar(lista, ahora);
                lista.addLast(ahora);
            }
            if (porIp.size() > 1000) {
                purgar(ahora);
            }
        }

        private void purgar(Instant ahora) {
            porIp.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    limpiar(e.getValue(), ahora);
                    return e.getValue().isEmpty();
                }
            });
        }

        private static void limpiar(Deque<Instant> lista, Instant ahora) {
            while (!lista.isEmpty() && lista.peekFirst().isBefore(ahora.minus(VENTANA))) {
                lista.pollFirst();
            }
        }

        private static String clave(String ip) {
            return ip == null ? "?" : ip;
        }
    }
}
