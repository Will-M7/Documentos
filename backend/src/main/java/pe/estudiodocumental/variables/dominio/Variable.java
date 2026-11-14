package pe.estudiodocumental.variables.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.regex.Pattern;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Variable del catálogo compartido. La clave es estable: no cambia después de creada. */
@Entity
@Table(name = "variable")
public class Variable extends EntidadBase {

    public static final Pattern FORMATO_CLAVE = Pattern.compile("[a-z][a-z0-9_]*");

    @Column(nullable = false, unique = true, length = 60)
    private String clave;

    @Column(nullable = false, length = 120)
    private String etiqueta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoVariable tipo;

    @Column(name = "valor_defecto", length = 500)
    private String valorDefecto;

    @Column(length = 300)
    private String descripcion;

    protected Variable() {
        // Requerido por JPA
    }

    public Variable(String clave, String etiqueta, TipoVariable tipo, String valorDefecto, String descripcion) {
        this.clave = normalizarClave(clave);
        if (!FORMATO_CLAVE.matcher(this.clave).matches()) {
            throw new ReglaNegocioException(
                    "La clave '%s' no es válida: use minúsculas, números y guion bajo, empezando con una letra."
                            .formatted(clave));
        }
        editar(etiqueta, tipo, valorDefecto, descripcion);
    }

    /** Crea una variable con valores por defecto a partir de una clave encontrada en una plantilla. */
    public static Variable detectada(String clave) {
        return new Variable(clave, etiquetaDesde(clave), TipoVariable.TEXTO, null, "Detectada en una plantilla");
    }

    public void editar(String etiqueta, TipoVariable tipo, String valorDefecto, String descripcion) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new ReglaNegocioException("Escriba la etiqueta de la variable.");
        }
        this.etiqueta = etiqueta.strip();
        this.tipo = tipo == null ? TipoVariable.TEXTO : tipo;
        this.valorDefecto = vacioANulo(valorDefecto);
        this.descripcion = vacioANulo(descripcion);
    }

    public static String normalizarClave(String clave) {
        return clave == null ? "" : clave.strip().toLowerCase(Locale.ROOT);
    }

    /** "fecha_de_pago" → "Fecha de pago". */
    static String etiquetaDesde(String clave) {
        String texto = clave.replace('_', ' ').strip();
        return texto.isEmpty() ? clave : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    public String getClave() {
        return clave;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public TipoVariable getTipo() {
        return tipo;
    }

    public String getValorDefecto() {
        return valorDefecto;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
