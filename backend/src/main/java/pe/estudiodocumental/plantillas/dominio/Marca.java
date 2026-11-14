package pe.estudiodocumental.plantillas.dominio;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Una marca escrita en la plantilla Word. Formas admitidas:
 *
 * <ul>
 *   <li>{@code {{fecha}}}: variable del catálogo.
 *   <li>{@code {{otorgante.nombres}}}: dato de un participante (se llena desde Clientes).
 *   <li>{@code {{monto | letras}}}: cualquiera de las anteriores con un formato.
 * </ul>
 *
 * @param participante clave del participante, o null si es una variable simple.
 * @param campo clave de la variable o del campo del participante.
 * @param filtro formato a aplicar, o null.
 */
public record Marca(String participante, String campo, String filtro) {

    public static final Pattern PATRON = Pattern.compile(
            "\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)(?:\\.([A-Za-z][A-Za-z0-9_]*))?\\s*(?:\\|\\s*([A-Za-z_]+)\\s*)?}}");

    /** Formatos disponibles con {@code | nombre}. Ver documentos/aplicacion/Formateador. */
    public static final Set<String> FILTROS =
            Set.of("mayusculas", "minusculas", "titulo", "fecha_larga", "moneda", "letras");

    public static Marca desde(Matcher coincidencia) {
        String primero = minusculas(coincidencia.group(1));
        String segundo = minusculas(coincidencia.group(2));
        String filtro = minusculas(coincidencia.group(3));
        return segundo == null ? new Marca(null, primero, filtro) : new Marca(primero, segundo, filtro);
    }

    public boolean esDeParticipante() {
        return participante != null;
    }

    public Optional<String> filtroOpcional() {
        return Optional.ofNullable(filtro);
    }

    /** Como se ve en Word, normalizada: {@code {{otorgante.nombres | mayusculas}}}. */
    public String texto() {
        String base = esDeParticipante() ? participante + "." + campo : campo;
        return "{{" + base + (filtro == null ? "" : " | " + filtro) + "}}";
    }

    private static String minusculas(String texto) {
        return texto == null ? null : texto.toLowerCase(Locale.ROOT);
    }
}
