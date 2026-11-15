package pe.estudiodocumental.identidad.aplicacion;

import pe.estudiodocumental.comun.error.ReglaNegocioException;

/** Reglas mínimas para las contraseñas. */
final class PoliticaClave {

    static final int LONGITUD_MINIMA = 10;

    private PoliticaClave() {}

    static String validar(String clave) {
        if (clave == null || clave.length() < LONGITUD_MINIMA) {
            throw new ReglaNegocioException(
                    "La contraseña debe tener al menos %d caracteres.".formatted(LONGITUD_MINIMA));
        }
        if (clave.isBlank() || clave.chars().distinct().count() < 4) {
            throw new ReglaNegocioException("La contraseña es demasiado simple.");
        }
        return clave;
    }
}
