package pe.estudiodocumental.variables.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import pe.estudiodocumental.variables.dominio.TipoVariable;
import pe.estudiodocumental.variables.dominio.Variable;

/** Entrada y salida de la API de variables. */
public final class VariableDtos {

    private VariableDtos() {}

    public record CrearVariableRequest(
            @NotBlank(message = "Escriba la clave") @Size(max = 60)
            String clave,

            @NotBlank(message = "Escriba la etiqueta") @Size(max = 120)
            String etiqueta,

            @NotNull(message = "Elija el tipo") TipoVariable tipo,

            @Size(max = 500) String valorDefecto,
            @Size(max = 300) String descripcion) {}

    public record EditarVariableRequest(
            @NotBlank(message = "Escriba la etiqueta") @Size(max = 120)
            String etiqueta,

            @NotNull(message = "Elija el tipo") TipoVariable tipo,

            @Size(max = 500) String valorDefecto,
            @Size(max = 300) String descripcion) {}

    public record VariableResponse(
            UUID id, String clave, String etiqueta, TipoVariable tipo, String valorDefecto, String descripcion) {

        public static VariableResponse desde(Variable v) {
            return new VariableResponse(
                    v.getId(), v.getClave(), v.getEtiqueta(), v.getTipo(), v.getValorDefecto(), v.getDescripcion());
        }
    }
}
