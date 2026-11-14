package pe.estudiodocumental.consultas.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import pe.estudiodocumental.clientes.api.dto.ClienteResponse;
import pe.estudiodocumental.consultas.dominio.CredencialApi;
import pe.estudiodocumental.consultas.dominio.Proveedor;

/** Entrada y salida de las APIs de consultas y credenciales. */
public final class ConsultaDtos {

    private ConsultaDtos() {}

    /**
     * @param encontrado si hay datos para usar.
     * @param origen "CLIENTES", "Factiliza", "APIsPERU" o null.
     */
    public record ConsultaResponse(boolean encontrado, String origen, ClienteResponse cliente, String mensaje) {}

    /** El token nunca se devuelve: solo sus últimos caracteres. */
    public record CredencialResponse(
            UUID id,
            Proveedor proveedor,
            String nombre,
            String tokenFinal,
            int prioridad,
            boolean activo,
            String ultimoEstado,
            Instant ultimoUso) {

        public static CredencialResponse desde(CredencialApi c) {
            return new CredencialResponse(
                    c.getId(),
                    c.getProveedor(),
                    c.getNombre(),
                    c.getTokenFinal(),
                    c.getPrioridad(),
                    c.isActivo(),
                    c.getUltimoEstado(),
                    c.getUltimoUso());
        }
    }

    public record CrearCredencialRequest(
            @NotNull(message = "Elija el proveedor") Proveedor proveedor,
            @Size(max = 80) String nombre,

            @NotNull(message = "Pegue el token") @Size(max = 1000)
            String token,

            @Min(1) @Max(99) int prioridad) {}

    public record EditarCredencialRequest(
            @Size(max = 80) String nombre,
            @Size(max = 1000) String token,
            @Min(1) @Max(99) int prioridad,
            boolean activo) {}

    public record ResumenCredenciales(long consultasDelMes, java.util.List<CredencialResponse> credenciales) {}
}
