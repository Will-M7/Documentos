package pe.estudiodocumental.solicitudes.dominio;

public enum EstadoSolicitud {
    /** Recién enviada por el cliente; la oficina debe revisarla. */
    PENDIENTE,
    /** La oficina generó el documento. */
    ATENDIDA,
    /** La oficina no la atenderá (datos incompletos, duplicada…). */
    RECHAZADA
}
