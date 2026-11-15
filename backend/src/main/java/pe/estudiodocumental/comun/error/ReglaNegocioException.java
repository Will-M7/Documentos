package pe.estudiodocumental.comun.error;

/** La operación viola una regla del negocio. Se responde con HTTP 422. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
