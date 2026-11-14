package pe.estudiodocumental.comun.error;

/** Credenciales inválidas o usuario inactivo. Se responde con HTTP 401. */
public class NoAutenticadoException extends RuntimeException {

    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
