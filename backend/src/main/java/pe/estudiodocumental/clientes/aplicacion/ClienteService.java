package pe.estudiodocumental.clientes.aplicacion;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.clientes.dominio.Cliente;
import pe.estudiodocumental.clientes.dominio.DatosCliente;
import pe.estudiodocumental.clientes.dominio.OrigenDatos;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;
import pe.estudiodocumental.clientes.infraestructura.ClienteRepository;
import pe.estudiodocumental.comun.error.ConflictoException;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;

/**
 * Casos de uso de clientes. Es la única puerta de entrada al módulo: otros módulos (documentos, consultas) usan
 * este servicio y nunca el repositorio.
 */
@Service
public class ClienteService {

    private static final int TAMANO_MAXIMO = 100;

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Transactional(readOnly = true)
    public Page<Cliente> buscar(String texto, int pagina, int tamano) {
        String filtro = texto == null ? "" : texto.strip();
        var orden = Sort.by("apellidoPaterno", "razonSocial", "nombres");
        return clientes.buscar(
                filtro, PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO), orden));
    }

    @Transactional(readOnly = true)
    public Cliente obtener(UUID id) {
        return clientes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe."));
    }

    @Transactional(readOnly = true)
    public Optional<Cliente> buscarPorDocumento(TipoDocumento tipo, String numero) {
        return clientes.findByTipoDocumentoAndNumeroDocumento(tipo, TipoDocumento.normalizar(numero))
                .filter(Cliente::isActivo);
    }

    @Transactional
    public Cliente crear(DatosCliente datos) {
        clientes.findByTipoDocumentoAndNumeroDocumento(datos.tipoDocumento(), datos.numeroDocumento())
                .ifPresent(existente -> {
                    if (existente.isActivo()) {
                        throw new ConflictoException("Ya existe un cliente con %s %s: %s."
                                .formatted(
                                        datos.tipoDocumento(), datos.numeroDocumento(), existente.getNombreCompleto()));
                    }
                });
        // Si estaba dado de baja, se reactiva con los datos nuevos en lugar de duplicarlo
        Cliente cliente = clientes.findByTipoDocumentoAndNumeroDocumento(datos.tipoDocumento(), datos.numeroDocumento())
                .map(existente -> {
                    existente.actualizar(datos);
                    return existente;
                })
                .orElseGet(() -> new Cliente(datos, OrigenDatos.MANUAL));
        return clientes.save(cliente);
    }

    @Transactional
    public Cliente actualizar(UUID id, DatosCliente datos) {
        Cliente cliente = obtener(id);
        clientes.findByTipoDocumentoAndNumeroDocumento(datos.tipoDocumento(), datos.numeroDocumento())
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException(
                            "Ese documento ya pertenece a otro cliente: %s.".formatted(otro.getNombreCompleto()));
                });
        cliente.actualizar(datos);
        return cliente;
    }

    @Transactional
    public void desactivar(UUID id) {
        obtener(id).desactivar();
    }

    /**
     * Guarda los datos de un participante sin pedir confirmación: si el cliente existe lo completa (sin borrar
     * datos que ya tenía), si no, lo crea. Lo usan la generación de documentos y las consultas DNI/RUC.
     */
    @Transactional
    public Cliente guardarOCompletar(DatosCliente datos, OrigenDatos origen) {
        return clientes.findByTipoDocumentoAndNumeroDocumento(datos.tipoDocumento(), datos.numeroDocumento())
                .map(existente -> {
                    existente.completar(datos, origen);
                    return existente;
                })
                .orElseGet(() -> clientes.save(new Cliente(datos, origen)));
    }
}
