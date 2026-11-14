package pe.estudiodocumental.documentos.aplicacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convierte números a palabras en español, como se escriben en documentos legales del Perú.
 *
 * <ul>
 *   <li>{@code entero(1521)} → "MIL QUINIENTOS VEINTIUNO"
 *   <li>{@code soles(1500.5)} → "MIL QUINIENTOS CON 50/100 SOLES"
 * </ul>
 */
final class NumeroALetras {

    private static final String[] UNIDADES = {
        "",
        "UN",
        "DOS",
        "TRES",
        "CUATRO",
        "CINCO",
        "SEIS",
        "SIETE",
        "OCHO",
        "NUEVE",
        "DIEZ",
        "ONCE",
        "DOCE",
        "TRECE",
        "CATORCE",
        "QUINCE",
        "DIECISÉIS",
        "DIECISIETE",
        "DIECIOCHO",
        "DIECINUEVE",
        "VEINTE",
        "VEINTIÚN",
        "VEINTIDÓS",
        "VEINTITRÉS",
        "VEINTICUATRO",
        "VEINTICINCO",
        "VEINTISÉIS",
        "VEINTISIETE",
        "VEINTIOCHO",
        "VEINTINUEVE"
    };
    private static final String[] DECENAS = {
        "", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"
    };
    private static final String[] CENTENAS = {
        "",
        "CIENTO",
        "DOSCIENTOS",
        "TRESCIENTOS",
        "CUATROCIENTOS",
        "QUINIENTOS",
        "SEISCIENTOS",
        "SETECIENTOS",
        "OCHOCIENTOS",
        "NOVECIENTOS"
    };

    private NumeroALetras() {}

    /** Monto en soles con céntimos en fracción: "CIEN CON 00/100 SOLES". */
    static String soles(BigDecimal monto) {
        BigDecimal redondeado = monto.abs().setScale(2, RoundingMode.HALF_UP);
        long enteros = redondeado.longValue();
        int centimos = redondeado.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String moneda = enteros == 1 ? "SOL" : "SOLES";
        return "%s CON %02d/100 %s".formatted(entero(enteros), centimos, moneda);
    }

    /** Parte entera en palabras; los decimales, si hay, se leen como fracción "CON 5/10". */
    static String numero(BigDecimal valor) {
        BigDecimal abs = valor.abs().stripTrailingZeros();
        String texto = entero(abs.longValue());
        if (abs.scale() > 0) {
            BigDecimal fraccion = abs.remainder(BigDecimal.ONE);
            texto += " CON %s/%s"
                    .formatted(
                            fraccion.unscaledValue(),
                            BigDecimal.TEN.pow(fraccion.scale()).toPlainString());
        }
        return valor.signum() < 0 ? "MENOS " + texto : texto;
    }

    static String entero(long n) {
        if (n == 0) {
            return "CERO";
        }
        if (n >= 1_000_000_000_000L) {
            throw new IllegalArgumentException("Número demasiado grande para escribir en letras.");
        }
        StringBuilder texto = new StringBuilder();
        long millones = n / 1_000_000;
        long miles = (n / 1_000) % 1_000;
        long resto = n % 1_000;
        if (millones > 0) {
            texto.append(millones == 1 ? "UN MILLÓN" : hastaMil((int) millones, false) + " MILLONES");
        }
        if (miles > 0) {
            espacio(texto).append(miles == 1 ? "MIL" : hastaMil((int) miles, false) + " MIL");
        }
        if (resto > 0) {
            espacio(texto).append(hastaMil((int) resto, true));
        }
        return texto.toString();
    }

    /** 1 a 999. {@code final} indica que es el último grupo: "UNO" en lugar de "UN", "VEINTIUNO". */
    private static String hastaMil(int n, boolean esFinal) {
        if (n == 100) {
            return "CIEN";
        }
        StringBuilder texto = new StringBuilder(CENTENAS[n / 100]);
        int decenas = n % 100;
        if (decenas > 0) {
            espacio(texto).append(hastaCien(decenas, esFinal));
        }
        return texto.toString();
    }

    private static String hastaCien(int n, boolean esFinal) {
        if (n < 30) {
            if (esFinal && n == 1) {
                return "UNO";
            }
            if (esFinal && n == 21) {
                return "VEINTIUNO";
            }
            return UNIDADES[n];
        }
        int unidad = n % 10;
        String decena = DECENAS[n / 10];
        if (unidad == 0) {
            return decena;
        }
        return decena + " Y " + (esFinal && unidad == 1 ? "UNO" : UNIDADES[unidad]);
    }

    private static StringBuilder espacio(StringBuilder texto) {
        return texto.isEmpty() ? texto : texto.append(' ');
    }
}
