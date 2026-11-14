package pe.estudiodocumental.plantillas.aplicacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.archivos.aplicacion.ArchivoService;
import pe.estudiodocumental.archivos.dominio.Archivo;
import pe.estudiodocumental.comun.error.ConflictoException;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.eventos.EventoAuditoria;
import pe.estudiodocumental.plantillas.dominio.CampoParticipante;
import pe.estudiodocumental.plantillas.dominio.Marca;
import pe.estudiodocumental.plantillas.dominio.Plantilla;
import pe.estudiodocumental.plantillas.dominio.PlantillaVersion;
import pe.estudiodocumental.plantillas.dominio.TipoParticipante;
import pe.estudiodocumental.plantillas.infraestructura.MotorDocx;
import pe.estudiodocumental.plantillas.infraestructura.PlantillaRepository;
import pe.estudiodocumental.plantillas.infraestructura.PlantillaVersionRepository;
import pe.estudiodocumental.variables.aplicacion.VariableService;
import pe.estudiodocumental.variables.dominio.Variable;

/** Casos de uso de plantillas. Puerta de entrada del módulo para el módulo de documentos. */
@Service
public class PlantillaService {

    private static final long TAMANO_MAXIMO = 20L * 1024 * 1024;

    private final PlantillaRepository plantillas;
    private final PlantillaVersionRepository versiones;
    private final MotorDocx motor;
    private final ArchivoService archivos;
    private final VariableService variables;

    private final ApplicationEventPublisher eventos;

    public PlantillaService(
            PlantillaRepository plantillas,
            PlantillaVersionRepository versiones,
            MotorDocx motor,
            ArchivoService archivos,
            VariableService variables,
            ApplicationEventPublisher eventos) {
        this.plantillas = plantillas;
        this.versiones = versiones;
        this.motor = motor;
        this.archivos = archivos;
        this.variables = variables;
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public List<Plantilla> listar(boolean soloActivas) {
        return soloActivas
                ? plantillas.findByActivoTrueOrderByCategoriaAscNombreAsc()
                : plantillas.findAllByOrderByCategoriaAscNombreAsc();
    }

    @Transactional(readOnly = true)
    public Plantilla obtener(UUID id) {
        Plantilla plantilla =
                plantillas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("La plantilla no existe."));
        // Carga las colecciones dentro de la transacción (open-in-view está desactivado)
        plantilla.getParticipantes().forEach(p -> p.getCampos().size());
        plantilla.getVariables().size();
        return plantilla;
    }

    @Transactional(readOnly = true)
    public List<PlantillaVersion> versiones(UUID id) {
        return plantillas.versiones(id);
    }

    @Transactional(readOnly = true)
    public List<Variable> variablesDe(Plantilla plantilla) {
        return variables.porIds(
                plantilla.getVariables().stream().map(v -> v.getVariableId()).toList());
    }

    @Transactional
    public Plantilla crear(String nombre, String descripcion, String categoria, String nombreArchivo, byte[] docx) {
        if (plantillas.buscarPorNombre(nombre == null ? "" : nombre.strip()).isPresent()) {
            throw new ConflictoException("Ya existe una plantilla llamada '%s'.".formatted(nombre.strip()));
        }
        Plantilla plantilla = new Plantilla(nombre, descripcion, categoria);
        registrarVersion(plantilla, nombreArchivo, docx);
        eventos.publishEvent(
                EventoAuditoria.de("PLANTILLA_SUBIDA", "plantilla", plantilla.getId(), plantilla.getNombre()));
        return plantilla;
    }

    @Transactional
    public Plantilla subirVersion(UUID id, String nombreArchivo, byte[] docx) {
        Plantilla plantilla = obtener(id);
        registrarVersion(plantilla, nombreArchivo, docx);
        eventos.publishEvent(EventoAuditoria.de(
                "PLANTILLA_VERSION", "plantilla", id, plantilla.getNombre() + " v" + plantilla.getVersionActual()));
        return plantilla;
    }

    @Transactional
    public Plantilla editar(
            UUID id,
            String nombre,
            String descripcion,
            String categoria,
            boolean activo,
            List<ConfiguracionParticipante> participantes) {
        Plantilla plantilla = obtener(id);
        plantillas
                .buscarPorNombre(nombre == null ? "" : nombre.strip())
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new ConflictoException("Ya existe otra plantilla llamada '%s'.".formatted(nombre.strip()));
                });
        plantilla.editar(nombre, descripcion, categoria);
        plantilla.activar(activo);
        for (ConfiguracionParticipante c : participantes) {
            plantilla.participante(c.clave()).configurar(c.etiqueta(), c.tipoCliente());
        }
        eventos.publishEvent(EventoAuditoria.de("PLANTILLA_EDITADA", "plantilla", id, plantilla.getNombre()));
        return plantilla;
    }

    @Transactional(readOnly = true)
    public ArchivoService.Contenido archivoDeVersion(UUID id, int numero) {
        PlantillaVersion version = plantillas
                .version(id, numero)
                .orElseThrow(() -> new RecursoNoEncontradoException("La versión no existe."));
        return archivos.leer(version.getArchivoId());
    }

    /**
     * Rellena la versión actual de la plantilla. Lo usa el módulo de documentos.
     *
     * @param valor qué texto poner en cada marca.
     */
    @Transactional(readOnly = true)
    public Relleno rellenarVersionActual(UUID id, Function<Marca, String> valor) {
        Plantilla plantilla = obtener(id);
        if (!plantilla.isActivo()) {
            throw new ReglaNegocioException("La plantilla está desactivada.");
        }
        PlantillaVersion version = plantillas
                .version(id, plantilla.getVersionActual())
                .orElseThrow(() -> new IllegalStateException("Falta la versión actual de la plantilla " + id));
        byte[] docx = motor.rellenar(archivos.leer(version.getArchivoId()).bytes(), valor);
        return new Relleno(version.getId(), version.getNumero(), docx);
    }

    /** Resultado de rellenar una plantilla. */
    public record Relleno(UUID versionId, int numeroVersion, byte[] docx) {}

    /** Etiqueta y tipo de cliente de un participante, editables por el administrador. */
    public record ConfiguracionParticipante(String clave, String etiqueta, TipoParticipante tipoCliente) {}

    private void registrarVersion(Plantilla plantilla, String nombreArchivo, byte[] docx) {
        if (docx == null || docx.length == 0) {
            throw new ReglaNegocioException("Adjunte el archivo Word (.docx).");
        }
        if (docx.length > TAMANO_MAXIMO) {
            throw new ReglaNegocioException("El archivo supera los 20 MB.");
        }
        List<Marca> marcas = motor.detectar(docx);
        Analisis analisis = analizar(marcas);
        Map<String, Variable> catalogo = variables.asegurar(analisis.variables());
        List<UUID> idsVariables =
                analisis.variables().stream().map(c -> catalogo.get(c).getId()).toList();

        int numero = plantilla.nuevaVersion(analisis.participantes(), idsVariables);
        plantillas.save(plantilla);
        Archivo archivo = archivos.guardar(nombreDocx(nombreArchivo), ArchivoService.MIME_DOCX, docx);
        versiones.save(new PlantillaVersion(plantilla.getId(), numero, archivo.getId(), marcas));
    }

    /** Separa las marcas en participantes y variables, y rechaza campos o formatos desconocidos. */
    private static Analisis analizar(List<Marca> marcas) {
        Map<String, List<String>> participantes = new LinkedHashMap<>();
        Set<String> claves = new LinkedHashSet<>();
        List<String> errores = new ArrayList<>();
        for (Marca marca : marcas) {
            marca.filtroOpcional()
                    .filter(f -> !Marca.FILTROS.contains(f))
                    .ifPresent(f -> errores.add("%s: el formato '%s' no existe (use: %s)."
                            .formatted(marca.texto(), f, String.join(", ", Marca.FILTROS))));
            if (marca.esDeParticipante()) {
                if (CampoParticipante.desdeClave(marca.campo()).isEmpty()) {
                    errores.add("%s: '%s' no es un dato de cliente (use: %s)."
                            .formatted(marca.texto(), marca.campo(), CampoParticipante.clavesDisponibles()));
                }
                List<String> campos = participantes.computeIfAbsent(marca.participante(), k -> new ArrayList<>());
                if (!campos.contains(marca.campo())) {
                    campos.add(marca.campo());
                }
            } else {
                claves.add(marca.campo());
            }
        }
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException("Corrija la plantilla en Word: " + String.join(" ", errores));
        }
        if (marcas.isEmpty()) {
            throw new ReglaNegocioException(
                    "La plantilla no tiene marcas. Escriba en Word, por ejemplo, {{fecha}} o {{otorgante.nombres}}.");
        }
        return new Analisis(participantes, List.copyOf(claves));
    }

    private record Analisis(Map<String, List<String>> participantes, List<String> variables) {}

    private static String nombreDocx(String nombre) {
        return nombre == null || nombre.isBlank() ? "plantilla.docx" : nombre;
    }
}
