package pe.estudiodocumental.identidad.api.dto;

import java.time.Instant;

/**
 * @param token token de acceso; dura poco (15 minutos por defecto).
 * @param tokenSesion se envía a /auth/renovar para obtener otro acceso sin contraseña.
 * @param sesionExpiraEn hasta cuándo se puede renovar.
 */
public record TokenResponse(
        String token, Instant expiraEn, String tokenSesion, Instant sesionExpiraEn, UsuarioResponse usuario) {}
