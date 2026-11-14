package pe.estudiodocumental.clientes.api.dto;

import java.time.Instant;
import java.util.UUID;
import pe.estudiodocumental.clientes.dominio.Cliente;
import pe.estudiodocumental.clientes.dominio.OrigenDatos;
import pe.estudiodocumental.clientes.dominio.TipoCliente;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;

public record ClienteResponse(
        UUID id,
        TipoCliente tipo,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String razonSocial,
        String nombreCompleto,
        String direccion,
        String telefono,
        String correo,
        OrigenDatos origen,
        Instant actualizadoEn) {

    public static ClienteResponse desde(Cliente c) {
        return new ClienteResponse(
                c.getId(),
                c.getTipo(),
                c.getTipoDocumento(),
                c.getNumeroDocumento(),
                c.getNombres(),
                c.getApellidoPaterno(),
                c.getApellidoMaterno(),
                c.getRazonSocial(),
                c.getNombreCompleto(),
                c.getDireccion(),
                c.getTelefono(),
                c.getCorreo(),
                c.getOrigen(),
                c.getActualizadoEn());
    }
}
