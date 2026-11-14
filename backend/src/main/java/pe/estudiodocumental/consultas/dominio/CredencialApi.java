package pe.estudiodocumental.consultas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Token de un proveedor. Se guarda cifrado; solo se muestran sus últimos caracteres. */
@Entity
@Table(name = "credencial_api")
public class CredencialApi extends EntidadBase {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Proveedor proveedor;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(name = "token_cifrado", nullable = false, length = 2000)
    private String tokenCifrado;

    @Column(name = "token_final", nullable = false, length = 8)
    private String tokenFinal;

    @Column(nullable = false)
    private int prioridad;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "ultimo_estado", length = 200)
    private String ultimoEstado;

    @Column(name = "ultimo_uso")
    private Instant ultimoUso;

    @Column(name = "limite_mensual")
    private Integer limiteMensual;

    @Column(name = "agotada_hasta")
    private Instant agotadaHasta;

    protected CredencialApi() {
        // Requerido por JPA
    }

    public CredencialApi(Proveedor proveedor, String nombre, String tokenCifrado, String tokenFinal, int prioridad) {
        this.proveedor = proveedor;
        this.tokenCifrado = tokenCifrado;
        this.tokenFinal = tokenFinal;
        editar(nombre, prioridad, true);
    }

    public void editar(String nombre, int prioridad, boolean activo) {
        this.nombre = nombre == null || nombre.isBlank() ? proveedor.nombre() : nombre.strip();
        this.prioridad = prioridad;
        this.activo = activo;
    }

    /** Consultas que permite el plan por mes; null = sin límite. Cambiarlo quita el estado "agotada". */
    public void cambiarLimiteMensual(Integer limiteMensual) {
        this.limiteMensual = limiteMensual == null || limiteMensual <= 0 ? null : limiteMensual;
        this.agotadaHasta = null;
    }

    /** El proveedor dijo que se acabó el cupo: no se usa hasta esa fecha. */
    public void marcarAgotada(Instant hasta) {
        this.agotadaHasta = hasta;
    }

    public boolean estaAgotada(Instant ahora) {
        return agotadaHasta != null && agotadaHasta.isAfter(ahora);
    }

    public void cambiarToken(String tokenCifrado, String tokenFinal) {
        this.tokenCifrado = tokenCifrado;
        this.tokenFinal = tokenFinal;
    }

    public void registrarUso(String estado) {
        this.ultimoUso = Instant.now();
        this.ultimoEstado = estado;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTokenCifrado() {
        return tokenCifrado;
    }

    public String getTokenFinal() {
        return tokenFinal;
    }

    public int getPrioridad() {
        return prioridad;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getUltimoEstado() {
        return ultimoEstado;
    }

    public Instant getUltimoUso() {
        return ultimoUso;
    }

    public Integer getLimiteMensual() {
        return limiteMensual;
    }

    public Instant getAgotadaHasta() {
        return agotadaHasta;
    }
}
