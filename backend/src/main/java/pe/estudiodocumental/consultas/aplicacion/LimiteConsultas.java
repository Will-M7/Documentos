package pe.estudiodocumental.consultas.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.estudiodocumental.comun.error.LimiteExcedidoException;

/**
 * Limita las consultas pagadas a proveedores por usuario y por minuto, para que un error (o un abuso) no gaste el
 * saldo. Las consultas resueltas con Clientes no cuentan. Se lleva en memoria: al reiniciar el sistema empieza de
 * cero, lo que basta para una sola PC servidor.
 */
@Component
class LimiteConsultas {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    private final int maximoPorMinuto;
    private final Clock reloj;
    private final Map<UUID, Deque<Instant>> uso = new ConcurrentHashMap<>();

    @Autowired
    LimiteConsultas(@Value("${app.consultas.limite-por-minuto:10}") int maximoPorMinuto) {
        this(maximoPorMinuto, Clock.systemUTC());
    }

    LimiteConsultas(int maximoPorMinuto, Clock reloj) {
        this.maximoPorMinuto = maximoPorMinuto;
        this.reloj = reloj;
    }

    /** Registra una consulta del usuario o lanza {@link LimiteExcedidoException} si ya llegó al máximo. */
    void registrar(UUID usuarioId) {
        Instant ahora = reloj.instant();
        Deque<Instant> recientes = uso.computeIfAbsent(usuarioId, k -> new ArrayDeque<>());
        synchronized (recientes) {
            while (!recientes.isEmpty() && recientes.peekFirst().isBefore(ahora.minus(VENTANA))) {
                recientes.pollFirst();
            }
            if (recientes.size() >= maximoPorMinuto) {
                long segundos = Math.max(
                        1,
                        Duration.between(ahora, recientes.peekFirst().plus(VENTANA))
                                .toSeconds());
                throw new LimiteExcedidoException(
                        "Se alcanzó el límite de %d consultas por minuto. Espere %d segundos o complete los datos a mano."
                                .formatted(maximoPorMinuto, segundos));
            }
            recientes.addLast(ahora);
        }
    }
}
