package pe.estudiodocumental.clientes.dominio;

import java.util.ArrayList;
import java.util.List;
import pe.estudiodocumental.comun.error.ReglaNegocioException;

/**
 * Datos de un cliente ya normalizados y validados según las reglas del negocio. Es la única forma de crear o
 * modificar un {@link Cliente}.
 */
public record DatosCliente(
        TipoCliente tipo,
        TipoDocumento tipoDocumento,
        String numeroDocumento,
        String nombres,
        String apellidoPaterno,
        String apellidoMaterno,
        String razonSocial,
        String direccion,
        String telefono,
        String correo) {

    public DatosCliente {
        numeroDocumento = TipoDocumento.normalizar(numeroDocumento);
        nombres = limpiar(nombres);
        apellidoPaterno = limpiar(apellidoPaterno);
        apellidoMaterno = limpiar(apellidoMaterno);
        razonSocial = limpiar(razonSocial);
        direccion = limpiar(direccion);
        telefono = limpiar(telefono);
        correo = limpiar(correo);
        if (tipo == TipoCliente.EMPRESA) {
            nombres = null;
            apellidoPaterno = null;
            apellidoMaterno = null;
        } else {
            razonSocial = null;
        }
        validar(tipo, tipoDocumento, numeroDocumento, nombres, apellidoPaterno, razonSocial);
    }

    private static void validar(
            TipoCliente tipo,
            TipoDocumento tipoDocumento,
            String numero,
            String nombres,
            String apellidoPaterno,
            String razonSocial) {
        List<String> errores = new ArrayList<>();
        if (tipo == null || tipoDocumento == null) {
            throw new ReglaNegocioException("Indique el tipo de cliente y el tipo de documento.");
        }
        if (!tipoDocumento.esValido(numero)) {
            errores.add(tipoDocumento.mensajeFormato());
        }
        if (tipo == TipoCliente.EMPRESA) {
            if (tipoDocumento != TipoDocumento.RUC) {
                errores.add("Una empresa se identifica con RUC.");
            }
            if (razonSocial == null) {
                errores.add("Escriba la razón social.");
            }
        } else {
            if (nombres == null) {
                errores.add("Escriba los nombres.");
            }
            if (apellidoPaterno == null) {
                errores.add("Escriba el apellido paterno.");
            }
        }
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException(String.join(" ", errores));
        }
    }

    /** Quita espacios sobrantes; un texto vacío se guarda como null. */
    private static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }
}
