package pe.estudiodocumental.plantillas.dominio;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/** Datos de un participante que se pueden usar en Word como {@code {{participante.campo}}}. */
public enum CampoParticipante {
    NOMBRES("Nombres"),
    APELLIDO_PATERNO("Apellido paterno"),
    APELLIDO_MATERNO("Apellido materno"),
    APELLIDOS("Apellidos"),
    NOMBRE_COMPLETO("Nombre completo o razón social"),
    RAZON_SOCIAL("Razón social"),
    TIPO_DOCUMENTO("Tipo de documento"),
    NUMERO_DOCUMENTO("Número de documento"),
    DOCUMENTO("Documento (tipo y número)"),
    DIRECCION("Dirección"),
    TELEFONO("Teléfono"),
    CORREO("Correo");

    private final String etiqueta;

    CampoParticipante(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String clave() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String etiqueta() {
        return etiqueta;
    }

    public static Optional<CampoParticipante> desdeClave(String clave) {
        return Arrays.stream(values()).filter(c -> c.clave().equals(clave)).findFirst();
    }

    public static String clavesDisponibles() {
        return Arrays.stream(values()).map(CampoParticipante::clave).collect(Collectors.joining(", "));
    }
}
