package pe.estudiodocumental.comun.error;

/** El recurso ya existe o choca con otro (por ejemplo, documento de identidad repetido). HTTP 409. */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
