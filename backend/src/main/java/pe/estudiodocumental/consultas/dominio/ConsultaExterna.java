package pe.estudiodocumental.consultas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.estudiodocumental.comun.persistencia.EntidadBase;

/** Registro de una llamada a un proveedor externo (para controlar el costo y diagnosticar fallas). */
@Entity
@Table(name = "consulta_externa")
public class ConsultaExterna extends EntidadBase {

    @Column(nullable = false, length = 5)
    private String tipo;

    @Column(nullable = false, length = 15)
    private String numero;

    @Column(nullable = false, length = 15)
    private String proveedor;

    @Column(nullable = false)
    private boolean exitosa;

    @Column(length = 300)
    private String mensaje;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    protected ConsultaExterna() {
        // Requerido por JPA
    }

    public ConsultaExterna(
            String tipo, String numero, Proveedor proveedor, boolean exitosa, String mensaje, UUID usuarioId) {
        this.tipo = tipo;
        this.numero = numero;
        this.proveedor = proveedor.name();
        this.exitosa = exitosa;
        this.mensaje = mensaje == null || mensaje.length() <= 300 ? mensaje : mensaje.substring(0, 300);
        this.usuarioId = usuarioId;
    }
}
