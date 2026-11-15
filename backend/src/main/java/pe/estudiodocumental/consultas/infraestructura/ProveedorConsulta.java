package pe.estudiodocumental.consultas.infraestructura;

import pe.estudiodocumental.consultas.dominio.Proveedor;

/**
 * Adaptador de un proveedor externo de DNI/RUC. Cada uno traduce su respuesta a {@link DatosConsultados}; agregar un
 * proveedor nuevo no toca el resto del sistema.
 */
public interface ProveedorConsulta {

    Proveedor proveedor();

    /**
     * @param tipo "DNI" o "RUC".
     * @throws FalloConsulta si no se obtuvo una respuesta utilizable.
     */
    DatosConsultados consultar(String tipo, String numero, String token);

    /** Datos que devuelve cualquier proveedor, en un formato común. */
    record DatosConsultados(
            String numero,
            String nombres,
            String apellidoPaterno,
            String apellidoMaterno,
            String razonSocial,
            String direccion) {}

    /** Motivo por el que falló una consulta. */
    enum Motivo {
        NO_ENCONTRADO("No se encontró ese número en el padrón."),
        CREDENCIAL_RECHAZADA("El proveedor rechazó el token: revise la credencial."),
        LIMITE("Se alcanzó el límite de consultas del proveedor."),
        SIN_CONEXION("No hay conexión con el proveedor (¿hay internet?)."),
        RESPUESTA_INVALIDA("El proveedor devolvió una respuesta que no se pudo leer.");

        private final String mensaje;

        Motivo(String mensaje) {
            this.mensaje = mensaje;
        }

        public String mensaje() {
            return mensaje;
        }
    }

    class FalloConsulta extends RuntimeException {

        private final Motivo motivo;

        public FalloConsulta(Motivo motivo, String detalle) {
            super(detalle == null || detalle.isBlank() ? motivo.mensaje() : motivo.mensaje() + " " + detalle);
            this.motivo = motivo;
        }

        public Motivo motivo() {
            return motivo;
        }
    }
}
