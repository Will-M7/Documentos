package pe.estudiodocumental.comun.error;

/** El recurso pedido no existe. Se responde con HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
