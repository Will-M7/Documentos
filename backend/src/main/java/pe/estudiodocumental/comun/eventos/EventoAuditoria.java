package pe.estudiodocumental.comun.eventos;

import java.util.UUID;

/**
 * Algo que debe quedar registrado en la auditoría. Los módulos lo publican con {@code ApplicationEventPublisher} y el
 * módulo de auditoría lo guarda, sin que los módulos dependan de él.
 *
 * @param accion qué pasó, en mayúsculas: {@code DOCUMENTO_GENERADO}, {@code INGRESO_FALLIDO}...
 * @param entidad tipo de objeto afectado: "documento", "cliente"...
 * @param entidadId id del objeto, o null.
 * @param detalle texto corto para personas: "N.º 12 Carta poder - Ana Quispe".
 * @param usuarioId quién lo hizo, solo si no es el usuario de la sesión (por ejemplo, en el ingreso); si es null se
 *     toma del token.
 */
public record EventoAuditoria(String accion, String entidad, UUID entidadId, String detalle, UUID usuarioId) {

    public static EventoAuditoria de(String accion, String entidad, UUID entidadId, String detalle) {
        return new EventoAuditoria(accion, entidad, entidadId, detalle, null);
    }
}
