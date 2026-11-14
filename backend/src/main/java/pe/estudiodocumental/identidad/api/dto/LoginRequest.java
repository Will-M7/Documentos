package pe.estudiodocumental.identidad.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Escriba el usuario") @Size(max = 50)
        String usuario,

        @NotBlank(message = "Escriba la contraseña") @Size(max = 100)
        String clave) {}
