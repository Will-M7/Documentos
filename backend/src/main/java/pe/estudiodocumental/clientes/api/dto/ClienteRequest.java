package pe.estudiodocumental.clientes.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.estudiodocumental.clientes.dominio.DatosCliente;
import pe.estudiodocumental.clientes.dominio.TipoCliente;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;

/** Datos para crear o editar un cliente. Las reglas de negocio se validan en {@link DatosCliente}. */
public record ClienteRequest(
        @NotNull(message = "Elija si es persona o empresa") TipoCliente tipo,

        @NotNull(message = "Elija el tipo de documento") TipoDocumento tipoDocumento,

        @NotBlank(message = "Escriba el número de documento") @Size(max = 15)
        String numeroDocumento,

        @Size(max = 120) String nombres,
        @Size(max = 80) String apellidoPaterno,
        @Size(max = 80) String apellidoMaterno,
        @Size(max = 200) String razonSocial,
        @Size(max = 250) String direccion,
        @Size(max = 30) String telefono,

        @Email(message = "Correo no válido") @Size(max = 120)
        String correo) {

    public DatosCliente aDatos() {
        return new DatosCliente(
                tipo,
                tipoDocumento,
                numeroDocumento,
                nombres,
                apellidoPaterno,
                apellidoMaterno,
                razonSocial,
                direccion,
                telefono,
                correo);
    }
}
