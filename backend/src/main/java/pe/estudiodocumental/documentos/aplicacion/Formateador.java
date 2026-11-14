package pe.estudiodocumental.documentos.aplicacion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.variables.dominio.TipoVariable;

/**
 * Convierte los valores ingresados en el texto que va en el Word, según el tipo de variable y el formato pedido
 * en la marca ({@code {{monto | letras}}}).
 */
final class Formateador {

    private static final Locale PERU = Locale.of("es", "PE");
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PERU);

    private Formateador() {}

    /**
     * @param valor texto tal como se ingresó (fecha en formato AAAA-MM-DD, números con punto decimal).
     * @param tipo tipo de la variable, o null para datos de clientes (texto).
     * @param filtro formato de la marca, o null.
     */
    static String formatear(String valor, TipoVariable tipo, String filtro) {
        if (valor == null || valor.isBlank()) {
            return "";
        }
        String texto = valor.strip();
        TipoVariable t = tipo == null ? TipoVariable.TEXTO : tipo;
        if (filtro == null) {
            return switch (t) {
                case FECHA -> fecha(texto).format(FECHA_CORTA);
                case MONEDA -> "S/ " + montoConMiles(numero(texto));
                default -> texto;
            };
        }
        return switch (filtro) {
            case "mayusculas" -> formatear(valor, tipo, null).toUpperCase(PERU);
            case "minusculas" -> formatear(valor, tipo, null).toLowerCase(PERU);
            case "titulo" -> titulo(formatear(valor, tipo, null));
            case "fecha_larga" -> fecha(texto).format(FECHA_LARGA);
            case "moneda" -> "S/ " + montoConMiles(numero(texto));
            case "letras" ->
                t == TipoVariable.MONEDA ? NumeroALetras.soles(numero(texto)) : NumeroALetras.numero(numero(texto));
            default -> texto;
        };
    }

    /** Valida el valor según su tipo; devuelve un mensaje de error o null si es válido. */
    static String validar(String valor, TipoVariable tipo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            switch (tipo) {
                case FECHA -> fecha(valor.strip());
                case NUMERO, MONEDA -> numero(valor.strip());
                default -> {
                    // Texto: siempre válido
                }
            }
            return null;
        } catch (ReglaNegocioException e) {
            return e.getMessage();
        }
    }

    private static LocalDate fecha(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (Exception e) {
            throw new ReglaNegocioException("'%s' no es una fecha válida (AAAA-MM-DD).".formatted(texto));
        }
    }

    private static BigDecimal numero(String texto) {
        try {
            return new BigDecimal(texto.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException("'%s' no es un número válido.".formatted(texto));
        }
    }

    private static String montoConMiles(BigDecimal monto) {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(Locale.US);
        return new DecimalFormat("#,##0.00", simbolos).format(monto);
    }

    private static String titulo(String texto) {
        StringBuilder salida = new StringBuilder(texto.length());
        boolean inicio = true;
        for (char c : texto.toLowerCase(PERU).toCharArray()) {
            salida.append(inicio ? Character.toUpperCase(c) : c);
            inicio = Character.isWhitespace(c);
        }
        return salida.toString();
    }
}
