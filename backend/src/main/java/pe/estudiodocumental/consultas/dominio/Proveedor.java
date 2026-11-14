package pe.estudiodocumental.consultas.dominio;

/** Proveedores externos de consulta de DNI y RUC. */
public enum Proveedor {
    FACTILIZA("Factiliza"),
    APISPERU("APIsPERU");

    private final String nombre;

    Proveedor(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }
}
