package pe.estudiodocumental.comun.eventos;

import java.util.UUID;

/**
 * Se generó un documento. Lo publica el módulo de documentos; el de solicitudes lo escucha para marcar como atendida la
 * solicitud de la que salió, sin que documentos dependa de solicitudes.
 *
 * @param solicitudId solicitud del portal que originó el documento, o null.
 */
public record DocumentoGenerado(UUID documentoId, UUID solicitudId, UUID usuarioId) {}
