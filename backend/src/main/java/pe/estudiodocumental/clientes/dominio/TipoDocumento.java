package pe.estudiodocumental.clientes.dominio;

import java.util.regex.Pattern;

/** Documentos de identidad admitidos y su formato. */
public enum TipoDocumento {
    DNI("\\d{8}", "El DNI debe tener 8 dígitos."),
    RUC("(10|15|17|20)\\d{9}", "El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20."),
    CE("[A-Z0-9]{8,12}", "El carné de extranjería debe tener de 8 a 12 letras o números."),
    PASAPORTE("[A-Z0-9]{6,12}", "El pasaporte debe tener de 6 a 12 letras o números.");

    private final Pattern formato;
    private final String mensaje;

    TipoDocumento(String formato, String mensaje) {
        this.formato = Pattern.compile(formato);
        this.mensaje = mensaje;
    }

    /** Quita espacios y guiones y pasa a mayúsculas: " 4567-8912 " → "45678912". */
    public static String normalizar(String numero) {
        return numero == null ? "" : numero.replaceAll("[\\s-]", "").toUpperCase();
    }

    public boolean esValido(String numeroNormalizado) {
        return formato.matcher(numeroNormalizado).matches();
    }

    public String mensajeFormato() {
        return mensaje;
    }
}
