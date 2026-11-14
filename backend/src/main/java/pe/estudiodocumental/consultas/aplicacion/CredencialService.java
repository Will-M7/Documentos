package pe.estudiodocumental.consultas.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.consultas.dominio.CredencialApi;
import pe.estudiodocumental.consultas.dominio.Proveedor;
import pe.estudiodocumental.consultas.infraestructura.CifradoTokens;
import pe.estudiodocumental.consultas.infraestructura.ConsultaExternaRepository;
import pe.estudiodocumental.consultas.infraestructura.CredencialRepository;
import pe.estudiodocumental.documentos.aplicacion.DocumentoService;

/** Alta, edición y baja de credenciales de proveedores. Solo administradores. */
@Service
public class CredencialService {

    private final CredencialRepository credenciales;
    private final ConsultaExternaRepository consultas;
    private final CifradoTokens cifrado;

    public CredencialService(
            CredencialRepository credenciales, ConsultaExternaRepository consultas, CifradoTokens cifrado) {
        this.credenciales = credenciales;
        this.consultas = consultas;
        this.cifrado = cifrado;
    }

    @Transactional(readOnly = true)
    public List<CredencialApi> listar() {
        return credenciales.findAllByOrderByPrioridadAscCreadoEnAsc();
    }

    @Transactional
    public CredencialApi crear(Proveedor proveedor, String nombre, String token, int prioridad) {
        String limpio = validarToken(token);
        return credenciales.save(
                new CredencialApi(proveedor, nombre, cifrado.cifrar(limpio), finalDe(limpio), prioridad));
    }

    /** Edita nombre, prioridad y estado; el token solo cambia si se envía uno nuevo. */
    @Transactional
    public CredencialApi editar(UUID id, String nombre, String tokenNuevo, int prioridad, boolean activo) {
        CredencialApi credencial = credenciales
                .findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La credencial no existe."));
        credencial.editar(nombre, prioridad, activo);
        if (tokenNuevo != null && !tokenNuevo.isBlank()) {
            String limpio = validarToken(tokenNuevo);
            credencial.cambiarToken(cifrado.cifrar(limpio), finalDe(limpio));
        }
        return credencial;
    }

    @Transactional
    public void eliminar(UUID id) {
        credenciales.deleteById(id);
    }

    /** Consultas externas hechas desde el inicio del mes (para vigilar el costo). */
    @Transactional(readOnly = true)
    public long consultasDelMes() {
        return consultas.contarDesde(LocalDate.now(DocumentoService.ZONA)
                .withDayOfMonth(1)
                .atStartOfDay(DocumentoService.ZONA)
                .toInstant());
    }

    private static String validarToken(String token) {
        if (token == null || token.strip().length() < 10) {
            throw new ReglaNegocioException("Pegue el token completo que le dio el proveedor.");
        }
        return token.strip();
    }

    private static String finalDe(String token) {
        return "…" + token.substring(Math.max(0, token.length() - 4));
    }
}
