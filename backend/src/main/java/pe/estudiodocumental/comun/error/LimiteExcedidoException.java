package pe.estudiodocumental.comun.error;

/** Se superó un límite de uso (por ejemplo, consultas pagadas por minuto). HTTP 429. */
public class LimiteExcedidoException extends RuntimeException {

    public LimiteExcedidoException(String mensaje) {
        super(mensaje);
    }
}
