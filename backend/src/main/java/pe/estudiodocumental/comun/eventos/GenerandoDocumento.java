package pe.estudiodocumental.comun.eventos;

import java.util.UUID;

/**
 * Se va a generar un documento para una solicitud del portal. Se publica ANTES de escribir archivos, dentro de la
 * transacción de la generación, para que el módulo de solicitudes la bloquee y verifique (que exista, siga pendiente y
 * sea de la misma plantilla). Si no corresponde, el listener lanza una excepción y no se genera nada.
 */
public record GenerandoDocumento(UUID plantillaId, UUID solicitudId) {}
