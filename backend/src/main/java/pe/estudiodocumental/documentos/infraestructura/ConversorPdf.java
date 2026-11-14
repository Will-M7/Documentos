package pe.estudiodocumental.documentos.infraestructura;

import java.util.Optional;

/** Convierte DOCX a PDF. Si no hay conversor disponible, el documento se entrega solo en Word. */
public interface ConversorPdf {

    /** El PDF, o vacío si el conversor no está instalado o falló (el motivo queda en {@link #estado()}). */
    Optional<byte[]> convertir(byte[] docx);

    /** null si está disponible; si no, un mensaje para mostrar al usuario. */
    String estado();
}
