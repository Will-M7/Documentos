package pe.estudiodocumental.documentos.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudiodocumental.archivos.aplicacion.ArchivoService;
import pe.estudiodocumental.clientes.aplicacion.ClienteService;
import pe.estudiodocumental.clientes.dominio.Cliente;
import pe.estudiodocumental.clientes.dominio.DatosCliente;
import pe.estudiodocumental.clientes.dominio.OrigenDatos;
import pe.estudiodocumental.clientes.dominio.TipoCliente;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;
import pe.estudiodocumental.comun.error.RecursoNoEncontradoException;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.documentos.dominio.Documento;
import pe.estudiodocumental.documentos.infraestructura.ConversorPdf;
import pe.estudiodocumental.documentos.infraestructura.DocumentoRepository;
import pe.estudiodocumental.plantillas.aplicacion.PlantillaService;
import pe.estudiodocumental.plantillas.dominio.ParticipantePlantilla;
import pe.estudiodocumental.plantillas.dominio.Plantilla;
import pe.estudiodocumental.plantillas.dominio.TipoParticipante;
import pe.estudiodocumental.variables.dominio.TipoVariable;
import pe.estudiodocumental.variables.dominio.Variable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Genera documentos: valida los datos, guarda o completa los clientes, rellena la plantilla, convierte a PDF y
 * registra todo en el historial.
 */
@Service
public class DocumentoService {

    public static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final DocumentoRepository documentos;
    private final PlantillaService plantillas;
    private final ClienteService clientes;
    private final ArchivoService archivos;
    private final ConversorPdf conversorPdf;
    private final JsonMapper json;

    public DocumentoService(
            DocumentoRepository documentos,
            PlantillaService plantillas,
            ClienteService clientes,
            ArchivoService archivos,
            ConversorPdf conversorPdf,
            JsonMapper json) {
        this.documentos = documentos;
        this.plantillas = plantillas;
        this.clientes = clientes;
        this.archivos = archivos;
        this.conversorPdf = conversorPdf;
        this.json = json;
    }

    /** Datos de un participante tal como llegan del formulario. */
    public record DatosParticipante(String clave, DatosCliente datos) {}

    @Transactional
    public Documento generar(
            UUID plantillaId,
            String titulo,
            Map<String, String> valoresIngresados,
            List<DatosParticipante> participantes,
            UUID usuarioId) {
        Plantilla plantilla = plantillas.obtener(plantillaId);
        if (!plantilla.isActivo()) {
            throw new ReglaNegocioException("La plantilla está desactivada.");
        }
        Map<String, String> ingresados = valoresIngresados == null ? Map.of() : valoresIngresados;
        List<Variable> variables = plantillas.variablesDe(plantilla);
        Map<String, String> valores = resolverVariables(variables, ingresados);
        Map<String, Cliente> porParticipante = guardarParticipantes(plantilla, participantes);

        Map<String, TipoVariable> tipos = new LinkedHashMap<>();
        variables.forEach(v -> tipos.put(v.getClave(), v.getTipo()));
        Map<String, String> marcas = new LinkedHashMap<>();
        var relleno = plantillas.rellenarVersionActual(plantillaId, marca -> {
            String texto = marca.esDeParticipante()
                    ? Formateador.formatear(
                            CamposCliente.valor(porParticipante.get(marca.participante()), marca.campo()),
                            null,
                            marca.filtro())
                    : Formateador.formatear(valores.get(marca.campo()), tipos.get(marca.campo()), marca.filtro());
            marcas.put(marca.texto(), texto);
            return texto;
        });

        String tituloFinal =
                titulo != null && !titulo.isBlank() ? titulo.strip() : tituloPorDefecto(plantilla, porParticipante);
        var docx = archivos.guardar(nombreArchivo(tituloFinal, "docx"), ArchivoService.MIME_DOCX, relleno.docx());
        Documento documento = new Documento(
                plantillaId,
                relleno.versionId(),
                plantilla.getNombre(),
                usuarioId,
                tituloFinal,
                json.writeValueAsString(Map.of("variables", valores, "marcas", marcas)),
                docx.getId());
        for (ParticipantePlantilla p : plantilla.getParticipantes()) {
            Cliente c = porParticipante.get(p.getClave());
            documento.agregarParticipante(
                    p.getClave(),
                    p.getEtiqueta(),
                    c.getId(),
                    c.getNombreCompleto(),
                    c.getTipoDocumento() + " " + c.getNumeroDocumento());
        }
        conversorPdf
                .convertir(relleno.docx())
                .map(pdf -> archivos.guardar(nombreArchivo(tituloFinal, "pdf"), ArchivoService.MIME_PDF, pdf))
                .ifPresent(pdf -> documento.adjuntarPdf(pdf.getId()));
        return documentos.saveAndFlush(documento);
    }

    @Transactional(readOnly = true)
    public Page<Documento> buscar(String texto, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        Instant inicio = (desde == null ? LocalDate.of(2000, 1, 1) : desde)
                .atStartOfDay(ZONA)
                .toInstant();
        Instant fin = (hasta == null ? LocalDate.now(ZONA) : hasta)
                .plusDays(1)
                .atStartOfDay(ZONA)
                .toInstant();
        Page<Documento> resultado = documentos.buscar(
                texto == null ? "" : texto.strip(),
                inicio,
                fin,
                PageRequest.of(
                        Math.max(pagina, 0),
                        Math.clamp(tamano, 1, 100),
                        Sort.by("creadoEn").descending()));
        resultado.forEach(d -> d.getParticipantes().size());
        return resultado;
    }

    @Transactional(readOnly = true)
    public Documento obtener(UUID id) {
        Documento documento =
                documentos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("El documento no existe."));
        documento.getParticipantes().size();
        return documento;
    }

    /** Valores ingresados y texto puesto en cada marca, leídos del JSON guardado. */
    public Map<String, Map<String, String>> valores(Documento documento) {
        return json.readValue(documento.getValores(), new TypeReference<>() {});
    }

    @Transactional(readOnly = true)
    public ArchivoService.Contenido archivo(UUID id, boolean pdf) {
        Documento documento = obtener(id);
        UUID archivoId = pdf ? documento.getPdfArchivoId() : documento.getDocxArchivoId();
        if (archivoId == null) {
            throw new RecursoNoEncontradoException(
                    Optional.ofNullable(conversorPdf.estado()).orElse("Este documento no tiene PDF."));
        }
        return archivos.leer(archivoId);
    }

    public String avisoPdf() {
        return conversorPdf.estado();
    }

    private static Map<String, String> resolverVariables(List<Variable> variables, Map<String, String> ingresados) {
        Map<String, String> valores = new LinkedHashMap<>();
        List<String> errores = new ArrayList<>();
        for (Variable v : variables) {
            String valor = Optional.ofNullable(ingresados.get(v.getClave()))
                    .filter(s -> !s.isBlank())
                    .orElse(v.getValorDefecto());
            if ((valor == null || valor.isBlank()) && v.getTipo() == TipoVariable.FECHA) {
                valor = LocalDate.now(ZONA).toString();
            }
            if (valor == null || valor.isBlank()) {
                errores.add("Complete «%s».".formatted(v.getEtiqueta()));
                continue;
            }
            String error = Formateador.validar(valor, v.getTipo());
            if (error != null) {
                errores.add("«%s»: %s".formatted(v.getEtiqueta(), error));
            }
            valores.put(v.getClave(), valor.strip());
        }
        if (!errores.isEmpty()) {
            throw new ReglaNegocioException(String.join(" ", errores));
        }
        return valores;
    }

    private Map<String, Cliente> guardarParticipantes(Plantilla plantilla, List<DatosParticipante> enviados) {
        Map<String, DatosCliente> porClave = new LinkedHashMap<>();
        if (enviados != null) {
            enviados.forEach(p -> porClave.put(p.clave(), p.datos()));
        }
        Map<String, Cliente> resultado = new LinkedHashMap<>();
        for (ParticipantePlantilla p : plantilla.getParticipantes()) {
            DatosCliente datos = porClave.get(p.getClave());
            if (datos == null) {
                throw new ReglaNegocioException("Complete los datos de «%s».".formatted(p.getEtiqueta()));
            }
            if (p.getTipoCliente() == TipoParticipante.PERSONA && datos.tipo() != TipoCliente.PERSONA) {
                throw new ReglaNegocioException("«%s» debe ser una persona.".formatted(p.getEtiqueta()));
            }
            if (p.getTipoCliente() == TipoParticipante.EMPRESA && datos.tipo() != TipoCliente.EMPRESA) {
                throw new ReglaNegocioException("«%s» debe ser una empresa.".formatted(p.getEtiqueta()));
            }
            try {
                resultado.put(p.getClave(), clientes.guardarOCompletar(datos, OrigenDatos.MANUAL));
            } catch (ReglaNegocioException e) {
                throw new ReglaNegocioException("«%s»: %s".formatted(p.getEtiqueta(), e.getMessage()));
            }
        }
        return resultado;
    }

    private static String tituloPorDefecto(Plantilla plantilla, Map<String, Cliente> participantes) {
        return participantes.values().stream()
                .findFirst()
                .map(c -> plantilla.getNombre() + " - " + c.getNombreCompleto())
                .orElse(plantilla.getNombre());
    }

    private static String nombreArchivo(String titulo, String extension) {
        String limpio = titulo.replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        return (limpio.length() > 120 ? limpio.substring(0, 120) : limpio) + "." + extension;
    }

    /** Texto de cada campo de cliente que se puede usar en Word. */
    static final class CamposCliente {

        private CamposCliente() {}

        static String valor(Cliente c, String campo) {
            if (c == null) {
                return "";
            }
            return switch (campo) {
                case "nombres" -> c.getNombres();
                case "apellido_paterno" -> c.getApellidoPaterno();
                case "apellido_materno" -> c.getApellidoMaterno();
                case "apellidos" -> unir(c.getApellidoPaterno(), c.getApellidoMaterno());
                case "nombre_completo" -> c.getNombreCompleto();
                case "razon_social" -> c.getRazonSocial() != null ? c.getRazonSocial() : c.getNombreCompleto();
                case "tipo_documento" -> nombreDocumento(c.getTipoDocumento());
                case "numero_documento" -> c.getNumeroDocumento();
                case "documento" -> nombreDocumento(c.getTipoDocumento()) + " N.º " + c.getNumeroDocumento();
                case "direccion" -> c.getDireccion();
                case "telefono" -> c.getTelefono();
                case "correo" -> c.getCorreo();
                default -> "";
            };
        }

        private static String nombreDocumento(TipoDocumento tipo) {
            return switch (tipo) {
                case DNI -> "DNI";
                case RUC -> "RUC";
                case CE -> "Carné de Extranjería";
                case PASAPORTE -> "Pasaporte";
            };
        }

        private static String unir(String a, String b) {
            return ((a == null ? "" : a) + " " + (b == null ? "" : b)).strip();
        }
    }
}
