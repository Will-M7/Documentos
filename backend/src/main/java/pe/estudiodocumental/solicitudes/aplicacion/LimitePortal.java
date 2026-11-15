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
 * Límite de solicitudes por dirección IP en el portal público, para que nadie llene la bandeja de la oficina con
 * envíos automáticos. Se guarda en memoria: al reiniciar el sistema se vuelve a empezar.
 */
@Component
class LimitePortal {

    private static final Duration VENTANA = Duration.ofMinutes(10);

    private final int maximo;
    private final Map<String, Deque<Instant>> envios = new ConcurrentHashMap<>();

    LimitePortal(@Value("${app.portal.solicitudes-por-10-minutos:5}") int maximo) {
        this.maximo = maximo;
    }

    void registrar(String ip) {
        Instant ahora = Instant.now();
        Deque<Instant> lista = envios.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (lista) {
            while (!lista.isEmpty() && lista.peekFirst().isBefore(ahora.minus(VENTANA))) {
                lista.pollFirst();
            }
            if (lista.size() >= maximo) {
                throw new LimiteExcedidoException(
                        "Se enviaron demasiadas solicitudes desde esta conexión. Intente de nuevo en unos minutos.");
            }
            lista.addLast(ahora);
        }
    }
}
