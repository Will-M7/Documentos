package pe.estudiodocumental.clientes.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Persona o empresa que participa en documentos. Única por tipo y número de documento. */
@Entity
@Table(name = "cliente")
public class Cliente extends EntidadBase {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoCliente tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 10)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 15)
    private String numeroDocumento;

    @Column(length = 120)
    private String nombres;

    @Column(name = "apellido_paterno", length = 80)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 80)
    private String apellidoMaterno;

    @Column(name = "razon_social", length = 200)
    private String razonSocial;

    @Column(length = 250)
    private String direccion;

    @Column(length = 30)
    private String telefono;

    @Column(length = 120)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrigenDatos origen;

    @Column(nullable = false)
    private boolean activo = true;

    protected Cliente() {
        // Requerido por JPA
    }

    public Cliente(DatosCliente datos, OrigenDatos origen) {
        this.origen = origen;
        aplicar(datos);
    }

    /** Reemplaza todos los datos (edición desde el formulario de clientes). */
    public void actualizar(DatosCliente datos) {
        aplicar(datos);
        this.activo = true;
    }

    /**
     * Completa con datos nuevos sin borrar los que ya había: un campo vacío en {@code datos} conserva el valor
     * actual. Se usa al guardar automáticamente desde un documento o una consulta DNI/RUC.
     */
    public void completar(DatosCliente datos, OrigenDatos nuevoOrigen) {
        DatosCliente combinados = new DatosCliente(
                datos.tipo(),
                datos.tipoDocumento(),
                datos.numeroDocumento(),
                primero(datos.nombres(), nombres),
                primero(datos.apellidoPaterno(), apellidoPaterno),
                primero(datos.apellidoMaterno(), apellidoMaterno),
                primero(datos.razonSocial(), razonSocial),
                primero(datos.direccion(), direccion),
                primero(datos.telefono(), telefono),
                primero(datos.correo(), correo));
        aplicar(combinados);
        if (nuevoOrigen != OrigenDatos.MANUAL) {
            this.origen = nuevoOrigen;
        }
        this.activo = true;
    }

    public void desactivar() {
        this.activo = false;
    }

    /** "Pérez Gómez, Ana María" para personas o la razón social para empresas. */
    public String getNombreCompleto() {
        if (tipo == TipoCliente.EMPRESA) {
            return razonSocial;
        }
        String apellidos = Stream.of(apellidoPaterno, apellidoMaterno)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
        return Stream.of(nombres, apellidos)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private void aplicar(DatosCliente datos) {
        this.tipo = datos.tipo();
        this.tipoDocumento = datos.tipoDocumento();
        this.numeroDocumento = datos.numeroDocumento();
        this.nombres = datos.nombres();
        this.apellidoPaterno = datos.apellidoPaterno();
        this.apellidoMaterno = datos.apellidoMaterno();
        this.razonSocial = datos.razonSocial();
        this.direccion = datos.direccion();
        this.telefono = datos.telefono();
        this.correo = datos.correo();
    }

    private static String primero(String nuevo, String actual) {
        return nuevo != null && !nuevo.isBlank() ? nuevo : actual;
    }

    public TipoCliente getTipo() {
        return tipo;
    }

    public TipoDocumento getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidoPaterno() {
        return apellidoPaterno;
    }

    public String getApellidoMaterno() {
        return apellidoMaterno;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public OrigenDatos getOrigen() {
        return origen;
    }

    public boolean isActivo() {
        return activo;
    }
}
