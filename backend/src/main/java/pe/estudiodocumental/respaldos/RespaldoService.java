package pe.estudiodocumental.respaldos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import pe.estudiodocumental.comun.error.ConflictoException;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.comun.eventos.EventoAuditoria;

/**
 * Muestra los respaldos que deja {@code respaldo.cmd} (una carpeta {@code AAAAMMDD_HHMM} por respaldo) y permite
 * lanzar uno al momento. El respaldo en sí lo sigue haciendo el script (pg_dump + copia de archivos), igual que
 * cuando lo ejecuta el Programador de tareas.
 */
@Service
public class RespaldoService {

    /** Pasado este tiempo sin respaldos, el sistema avisa al administrador. */
    static final Duration ANTIGUEDAD_MAXIMA = Duration.ofHours(48);

    private static final DateTimeFormatter NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
    private static final Duration TIEMPO_MAXIMO = Duration.ofMinutes(15);
    private static final int MAXIMO_LISTADOS = 30;

    private final Path directorio;
    private final Path script;
    private final ApplicationEventPublisher eventos;
    private final AtomicBoolean enCurso = new AtomicBoolean();

    RespaldoService(
            @Value("${app.respaldos.directorio:}") String directorio,
            @Value("${app.respaldos.script:}") String script,
            ApplicationEventPublisher eventos) {
        this.directorio = directorio.isBlank() ? null : Path.of(directorio);
        this.script = script.isBlank() ? null : Path.of(script);
        this.eventos = eventos;
    }

    public record Respaldo(String nombre, Instant fecha, long tamanoBytes, boolean completo) {}

    public record Estado(
            boolean configurado,
            String directorio,
            boolean puedeRespaldarAhora,
            boolean alDia,
            Instant ultimo,
            List<Respaldo> respaldos) {}

    public record Ejecucion(boolean exito, String salida) {}

    public Estado estado() {
        if (directorio == null) {
            return new Estado(false, null, false, false, null, List.of());
        }
        List<Respaldo> respaldos = listar();
        Instant ultimo = respaldos.stream()
                .filter(Respaldo::completo)
                .map(Respaldo::fecha)
                .max(Comparator.naturalOrder())
                .orElse(null);
        boolean alDia = ultimo != null && ultimo.isAfter(Instant.now().minus(ANTIGUEDAD_MAXIMA));
        return new Estado(true, directorio.toString(), scriptDisponible(), alDia, ultimo, respaldos);
    }

    /** Ejecuta el script de respaldo y espera a que termine. Solo uno a la vez. */
    public Ejecucion respaldarAhora() {
        if (!scriptDisponible()) {
            throw new ReglaNegocioException(
                    "El respaldo desde la web no está configurado en este equipo. Use respaldo.cmd (ver la guía de instalación).");
        }
        if (!enCurso.compareAndSet(false, true)) {
            throw new ConflictoException("Ya se está haciendo un respaldo. Espere a que termine.");
        }
        try {
            Ejecucion resultado = ejecutar();
            eventos.publishEvent(EventoAuditoria.de(
                    "RESPALDO_MANUAL",
                    "respaldo",
                    null,
                    resultado.exito() ? "Respaldo correcto" : "Respaldo con error"));
            return resultado;
        } finally {
            enCurso.set(false);
        }
    }

    private boolean scriptDisponible() {
        return script != null && Files.isRegularFile(script);
    }

    private Ejecucion ejecutar() {
        boolean windows =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> comando = windows ? List.of("cmd.exe", "/c", script.toString()) : List.of("sh", script.toString());
        Path registro = null;
        try {
            registro = Files.createTempFile("respaldo", ".log");
            Process proceso = new ProcessBuilder(comando)
                    .directory(script.toAbsolutePath().getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(registro.toFile())
                    .start();
            proceso.getOutputStream().close();
            if (!proceso.waitFor(TIEMPO_MAXIMO.toMinutes(), TimeUnit.MINUTES)) {
                // En Windows matar cmd.exe no detiene pg_dump ni robocopy: primero los procesos hijos
                proceso.descendants().forEach(ProcessHandle::destroyForcibly);
                proceso.destroyForcibly();
                return new Ejecucion(false, "El respaldo tardó más de 15 minutos y se detuvo.");
            }
            String salida = ultimasLineas(Files.readString(registro, Charset.defaultCharset()), 15);
            return new Ejecucion(proceso.exitValue() == 0, salida);
        } catch (IOException e) {
            return new Ejecucion(false, "No se pudo ejecutar el respaldo: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Ejecucion(false, "El respaldo se interrumpió.");
        } finally {
            if (registro != null) {
                try {
                    Files.deleteIfExists(registro);
                } catch (IOException ignorada) {
                    // archivo temporal: no es grave si queda
                }
            }
        }
    }

    private List<Respaldo> listar() {
        if (!Files.isDirectory(directorio)) {
            return List.of();
        }
        try (Stream<Path> carpetas = Files.list(directorio)) {
            return carpetas.filter(Files::isDirectory)
                    .map(RespaldoService::describir)
                    .sorted(Comparator.comparing(Respaldo::fecha).reversed())
                    .limit(MAXIMO_LISTADOS)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Respaldo describir(Path carpeta) {
        String nombre = carpeta.getFileName().toString();
        Instant fecha;
        try {
            fecha = LocalDateTime.parse(nombre, NOMBRE)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException e) {
            fecha = modificado(carpeta);
        }
        boolean completo = completo(carpeta);
        return new Respaldo(nombre, fecha, tamano(carpeta), completo);
    }

    /**
     * respaldo.cmd escribe {@code EN-CURSO.txt} al empezar y lo cambia por {@code RESPALDO-OK.txt} al terminar bien;
     * si falla a la mitad queda EN-CURSO. Los respaldos de antes de esas marcas se dan por buenos si tienen la base y
     * la carpeta de archivos (cuando pg_dump falla, el script se detiene antes de copiar los archivos).
     */
    static boolean completo(Path carpeta) {
        if (Files.isRegularFile(carpeta.resolve("RESPALDO-OK.txt"))) {
            return true;
        }
        if (Files.exists(carpeta.resolve("EN-CURSO.txt"))) {
            return false;
        }
        return Files.isRegularFile(carpeta.resolve("base.dump")) && Files.isDirectory(carpeta.resolve("archivos"));
    }

    private static Instant modificado(Path ruta) {
        try {
            return Files.getLastModifiedTime(ruta).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static long tamano(Path carpeta) {
        try (Stream<Path> archivos = Files.walk(carpeta)) {
            return archivos.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException | UncheckedIOException e) {
            // Un archivo puede desaparecer mientras se recorre (el script borra respaldos viejos)
            return 0;
        }
    }

    static String ultimasLineas(String texto, int cantidad) {
        List<String> lineas = new ArrayList<>(
                Arrays.stream(texto.split("\\R")).filter(l -> !l.isBlank()).toList());
        return String.join("\n", lineas.subList(Math.max(0, lineas.size() - cantidad), lineas.size()));
    }
}
