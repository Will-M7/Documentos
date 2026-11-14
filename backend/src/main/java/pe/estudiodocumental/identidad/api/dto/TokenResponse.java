package pe.estudiodocumental.identidad.api.dto;

import java.time.Instant;

public record TokenResponse(String token, Instant expiraEn, UsuarioResponse usuario) {}
