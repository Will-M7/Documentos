package pe.estudiodocumental.importacion.aplicacion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import pe.estudiodocumental.clientes.aplicacion.ClienteService;
import pe.estudiodocumental.clientes.dominio.DatosCliente;
import pe.estudiodocumental.clientes.dominio.OrigenDatos;
import pe.estudiodocumental.clientes.dominio.TipoCliente;
import pe.estudiodocumental.clientes.dominio.TipoDocumento;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.eventos.EventoAuditoria;
import pe.estudiodocumental.plantillas.aplicacion.PlantillaService;
import pe.estudiodocumental.plantillas.dominio.Plantilla;
import pe.estudiodocumental.variables.aplicacion.VariableService;
import pe.estudiodocumental.variables.dominio.TipoVariable;
import pe.estudiodocumental.variables.dominio.Variable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trae a la v2 lo exportado desde la aplicación de escritorio (v1): el ZIP de "Exportar clientes" y/o
 * "Exportar plantillas" ({@code clients.json} y {@code configuration.json}).
 *
 * <p>No es transaccional a propósito: cada cliente y cada plantilla se guarda por separado, así un registro con
 * datos malos se informa y no impide importar los demás. Se puede repetir sin duplicar: los clientes existentes
 * solo se completan y las plantillas con el mismo nombre se saltan.
 */
@Service
public class ImportacionV1Service {

    /** Mismo tope que usaba la v1 para un paquete descomprimido. */
    static final long MAXIMO_DESCOMPRIMIDO = 200L * 1024 * 1024;

    private static final int MAXIMO_AVISOS = 50;
    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "y", "san", "da", "di");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ClienteService clientes;
    private final PlantillaService plantillas;
    private final VariableService variables;
    private final ApplicationEventPublisher eventos;

    public ImportacionV1Service(
            ClienteService clientes,
            PlantillaService plantillas,
            VariableService variables,
            ApplicationEventPublisher eventos) {
        this.clientes = clientes;
        this.plantillas = plantillas;
        this.variables = variables;
        this.eventos = eventos;
    }

    public record Resultado(
            int clientesNuevos,
            int clientesCompletados,
            int clientesOmitidos,
            int plantillasCreadas,
            int plantillasOmitidas,
            List<String> avisos) {}

    public Resultado importar(byte[] zip) {
        Map<String, byte[]> archivos = leerZip(zip);
        if (!archivos.containsKey("clients.json") && !archivos.containsKey("configuration.json")) {
            throw new ReglaNegocioException(
                    "El archivo no es una exportación de la versión anterior: no tiene clients.json ni configuration.json.");
        }
        Avisos avisos = new Avisos();
        int[] cli = archivos.containsKey("clients.json")
                ? importarClientes(archivos.get("clients.json"), avisos)
                : new int[3];
        int[] pla = archivos.containsKey("configuration.json")
                ? importarPlantillas(archivos.get("configuration.json"), avisos)
                : new int[2];
        Resultado resultado = new Resultado(cli[0], cli[1], cli[2], pla[0], pla[1], avisos.lista());
        eventos.publishEvent(EventoAuditoria.de(
                "IMPORTACION_V1",
                "importacion",
                null,
                "Clientes: %d nuevos, %d completados, %d omitidos · Plantillas: %d creadas, %d omitidas"
                        .formatted(cli[0], cli[1], cli[2], pla[0], pla[1])));
        return resultado;
    }

    // --- ZIP ---

    private static Map<String, byte[]> leerZip(byte[] zip) {
        Map<String, byte[]> archivos = new HashMap<>();
        long total = 0;
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entrada0;
            boolean alguna = false;
            while ((entrada0 = entrada.getNextEntry()) != null) {
                alguna = true;
                String nombre = entrada0.getName();
                if (!nombre.equals("clients.json") && !nombre.equals("configuration.json")) {
                    continue;
                }
                if (archivos.containsKey(nombre)) {
                    throw new ReglaNegocioException("El ZIP tiene %s repetido.".formatted(nombre));
                }
                byte[] contenido = leerConTope(entrada, MAXIMO_DESCOMPRIMIDO - total);
                total += contenido.length;
                archivos.put(nombre, contenido);
            }
            if (!alguna) {
                throw new ReglaNegocioException("El archivo está vacío o no es un ZIP.");
            }
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer el ZIP: " + e.getMessage());
        }
        return archivos;
    }

    private static byte[] leerConTope(InputStream entrada, long tope) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] bufer = new byte[64 * 1024];
        long leidos = 0;
        int n;
        while ((n = entrada.read(bufer)) > 0) {
            leidos += n;
            if (leidos > tope) {
                throw new ReglaNegocioException("El ZIP descomprimido pasa de 200 MB.");
            }
            salida.write(bufer, 0, n);
        }
        return salida.toByteArray();
    }

    private static JsonNode leerJson(byte[] contenido, String formato, String archivo) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(contenido);
        } catch (JacksonException e) {
            throw new ReglaNegocioException("%s no es un JSON válido.".formatted(archivo));
        }
        if (raiz == null || !formato.equals(texto(raiz, "format"))) {
            throw new ReglaNegocioException("%s no tiene el formato esperado (%s).".formatted(archivo, formato));
        }
        return raiz;
    }

    // --- Clientes ---

    /** Devuelve {nuevos, completados, omitidos}. */
    private int[] importarClientes(byte[] contenido, Avisos avisos) {
        JsonNode raiz = leerJson(contenido, "document-studio-clients", "clients.json");
        int nuevos = 0;
        int completados = 0;
        int omitidos = 0;
        int fila = 0;
        for (JsonNode n : raiz.path("clients")) {
            fila++;
            try {
                DatosCliente datos = convertirCliente(n);
                boolean existia = clientes.buscarPorDocumento(datos.tipoDocumento(), datos.numeroDocumento())
                        .isPresent();
                clientes.guardarOCompletar(datos, OrigenDatos.MANUAL);
                if (existia) {
                    completados++;
                } else {
                    nuevos++;
                }
            } catch (RuntimeException e) {
                // ReglaNegocioException (dato inválido) u otro error de la base: se informa y se sigue con los demás
                omitidos++;
                String motivo = e instanceof ReglaNegocioException
                        ? e.getMessage()
                        : "no se pudo guardar (revise que los datos no sean demasiado largos).";
                avisos.agregar(
                        "Cliente %d (%s %s): %s".formatted(fila, texto(n, "documentType"), texto(n, "number"), motivo));
            }
        }
        return new int[] {nuevos, completados, omitidos};
    }

    static DatosCliente convertirCliente(JsonNode n) {
        boolean empresa = "Empresa".equalsIgnoreCase(texto(n, "kind"));
        String nombre = texto(n, "given");
        TipoDocumento tipoDocumento = empresa ? TipoDocumento.RUC : tipoDocumento(texto(n, "documentType"));
        Map<String, String> extras = extras(texto(n, "extrasJson"));
        // Datos adicionales: si no caben en la v2 se dejan fuera en vez de perder al cliente
        String telefono = siCabe(buscarExtra(extras, "telefono", "celular", "phone", "whatsapp"), 30);
        String correo = siCabe(buscarExtra(extras, "correo", "email", "mail"), 120);
        maximo(
                nombre,
                empresa ? 200 : 120,
                empresa ? "La razón social es demasiado larga." : "Los nombres son demasiado largos.");
        maximo(texto(n, "family"), 160, "Los apellidos son demasiado largos.");
        maximo(texto(n, "address"), 250, "La dirección es demasiado larga (máximo 250 letras).");
        if (empresa) {
            String razonSocial = String.join(" ", nombre, texto(n, "family")).strip();
            return new DatosCliente(
                    TipoCliente.EMPRESA,
                    tipoDocumento,
                    texto(n, "number"),
                    null,
                    null,
                    null,
                    razonSocial,
                    texto(n, "address"),
                    telefono,
                    correo);
        }
        String[] apellidos = separarApellidos(texto(n, "family"));
        return new DatosCliente(
                TipoCliente.PERSONA,
                tipoDocumento,
                texto(n, "number"),
                nombre,
                apellidos[0],
                apellidos[1],
                null,
                texto(n, "address"),
                telefono,
                correo);
    }

    private static String siCabe(String valor, int largo) {
        return valor == null || valor.strip().length() > largo ? null : valor.strip();
    }

    private static void maximo(String valor, int largo, String mensaje) {
        if (valor != null && valor.strip().length() > largo) {
            throw new ReglaNegocioException(mensaje);
        }
    }

    private static TipoDocumento tipoDocumento(String texto) {
        String t = texto.strip().toUpperCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("DNI")) {
            return TipoDocumento.DNI;
        }
        if (t.equals("RUC")) {
            return TipoDocumento.RUC;
        }
        if (t.equals("CE") || t.startsWith("CARN")) {
            return TipoDocumento.CE;
        }
        if (t.startsWith("PASAPORTE") || t.equals("PAS")) {
            return TipoDocumento.PASAPORTE;
        }
        throw new ReglaNegocioException("Tipo de documento desconocido: «%s».".formatted(texto));
    }

    /**
     * "Quispe Rojas" → [Quispe, Rojas]; "De la Cruz Rojas" → [De la Cruz, Rojas]. Las partículas (de, del, la...)
     * se pegan a la palabra que sigue. Si hay un solo apellido, el materno queda vacío.
     */
    public static String[] separarApellidos(String apellidos) {
        List<String> grupos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : apellidos.strip().split("\\s+")) {
            if (palabra.isEmpty()) {
                continue;
            }
            if (!actual.isEmpty()) {
                actual.append(' ');
            }
            actual.append(palabra);
            if (!PARTICULAS.contains(palabra.toLowerCase(Locale.ROOT))) {
                grupos.add(actual.toString());
                actual.setLength(0);
            }
        }
        if (!actual.isEmpty()) {
            grupos.add(actual.toString());
        }
        if (grupos.isEmpty()) {
            return new String[] {null, null};
        }
        String materno = grupos.size() > 1 ? String.join(" ", grupos.subList(1, grupos.size())) : null;
        return new String[] {grupos.get(0), materno};
    }

    private static Map<String, String> extras(String json) {
        Map<String, String> salida = new HashMap<>();
        if (json.isBlank()) {
            return salida;
        }
        try {
            JsonNode nodo = JSON.readTree(json);
            if (nodo != null && nodo.isObject()) {
                for (Map.Entry<String, JsonNode> e : nodo.properties()) {
                    if (e.getValue().isValueNode()) {
                        salida.put(
                                e.getKey().toLowerCase(Locale.ROOT),
                                e.getValue().asString());
                    }
                }
            }
        } catch (JacksonException e) {
            // Datos adicionales dañados: se ignoran, el cliente se importa igual
        }
        return salida;
    }

    private static String buscarExtra(Map<String, String> extras, String... palabras) {
        for (Map.Entry<String, String> e : extras.entrySet()) {
            for (String palabra : palabras) {
                if (e.getKey().contains(palabra) && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    // --- Plantillas ---

    /** Devuelve {creadas, omitidas}. */
    private int[] importarPlantillas(byte[] contenido, Avisos avisos) {
        JsonNode raiz = leerJson(contenido, "document-studio-config", "configuration.json");
        Map<Long, String> grupos = new HashMap<>();
        for (JsonNode g : raiz.path("groups")) {
            grupos.put(g.path("id").asLong(), texto(g, "name"));
        }
        Map<Long, JsonNode> definiciones = new HashMap<>();
        for (JsonNode d : raiz.path("definitions")) {
            definiciones.put(d.path("id").asLong(), d);
        }
        int creadas = 0;
        int omitidas = 0;
        for (JsonNode n : raiz.path("templates")) {
            String nombre = texto(n, "name");
            try {
                byte[] docx = decodificar(texto(n, "edited"))
                        .or(() -> decodificar(texto(n, "original")))
                        .orElseThrow(() -> new ReglaNegocioException("no trae el archivo Word."));
                Plantilla plantilla = plantillas.crear(
                        nombre, "Importada de la versión anterior", categoria(n, grupos), nombre + ".docx", docx);
                creadas++;
                try {
                    ajustarVariables(n, definiciones);
                } catch (RuntimeException e) {
                    avisos.agregar("Plantilla «%s»: se creó, pero revise sus variables (%s)."
                            .formatted(nombre, e.getMessage()));
                }
                if (plantilla.getVariables().isEmpty()
                        && plantilla.getParticipantes().isEmpty()) {
                    avisos.agregar("Plantilla «%s»: no tiene marcas {{...}}; revísela.".formatted(nombre));
                }
            } catch (RuntimeException e) {
                // Nombre repetido (ConflictoException), Word dañado, marcas inválidas...: se informa y se sigue
                omitidas++;
                avisos.agregar("Plantilla «%s»: %s".formatted(nombre, e.getMessage()));
            }
        }
        return new int[] {creadas, omitidas};
    }

    private static String categoria(JsonNode plantilla, Map<Long, String> grupos) {
        JsonNode ids = plantilla.path("groupIds");
        if (ids.isArray() && !ids.isEmpty()) {
            return grupos.get(ids.get(0).asLong());
        }
        return plantilla.has("groupId") ? grupos.get(plantilla.path("groupId").asLong()) : null;
    }

    /**
     * Las variables recién detectadas se crean con una etiqueta automática; aquí se les pone la etiqueta y el
     * tipo que tenían en la v1. Las que ya existían en la v2 (configuradas por el usuario) no se tocan.
     */
    private void ajustarVariables(JsonNode plantilla, Map<Long, JsonNode> definiciones) {
        Map<String, JsonNode> porClave = new HashMap<>();
        for (JsonNode v : plantilla.path("variables")) {
            JsonNode definicion = definiciones.get(v.path("definitionId").asLong());
            porClave.put(Variable.normalizarClave(texto(v, "key")), definicion != null ? definicion : v);
        }
        Map<String, Variable> existentes = variables.porClaves(porClave.keySet());
        for (Map.Entry<String, JsonNode> e : porClave.entrySet()) {
            Variable variable = existentes.get(e.getKey());
            if (variable == null || !"Detectada en una plantilla".equals(variable.getDescripcion())) {
                continue;
            }
            JsonNode origen = e.getValue();
            String etiqueta = texto(origen, "label");
            String defecto = texto(origen, "defaultValue");
            variables.editar(
                    variable.getId(),
                    etiqueta.isBlank() ? variable.getEtiqueta() : etiqueta,
                    tipoVariable(texto(origen, "type")),
                    defecto.isBlank() ? null : defecto,
                    "Importada de la versión anterior");
        }
    }

    static TipoVariable tipoVariable(String tipoV1) {
        return switch (tipoV1.strip()) {
            case "Fecha" -> TipoVariable.FECHA;
            case "Texto largo" -> TipoVariable.TEXTO_LARGO;
            case "Monto" -> TipoVariable.MONEDA;
            case "Número", "Número entero", "Número decimal" -> TipoVariable.NUMERO;
            default -> TipoVariable.TEXTO;
        };
    }

    private static Optional<byte[]> decodificar(String base64) {
        if (base64.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return bytes.length == 0 ? Optional.empty() : Optional.of(bytes);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        return valor.isMissingNode() || valor.isNull() ? "" : valor.asString();
    }

    /** Lista de avisos con tope, para no devolver miles de líneas si el archivo viene muy mal. */
    private static final class Avisos {
        private final List<String> lista = new ArrayList<>();
        private int resto;

        void agregar(String aviso) {
            if (lista.size() < MAXIMO_AVISOS) {
                lista.add(aviso);
            } else {
                resto++;
            }
        }

        List<String> lista() {
            if (resto > 0) {
                List<String> salida = new ArrayList<>(lista);
                salida.add("… y %d avisos más.".formatted(resto));
                return salida;
            }
            return lista;
        }
    }
}
