package pe.estudiodocumental.respaldos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;
import pe.estudiodocumental.comun.error.ReglaNegocioException;

class RespaldoServiceTest {

    private static final DateTimeFormatter NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    @TempDir
    Path tmp;

    private RespaldoService servicio(String directorio, String script) {
        return new RespaldoService(directorio, script, mock(ApplicationEventPublisher.class));
    }

    private Path respaldo(Path carpeta, LocalDateTime fecha, boolean conBase) throws Exception {
        Path destino = Files.createDirectories(carpeta.resolve(fecha.format(NOMBRE)));
        if (conBase) {
            Files.write(destino.resolve("base.dump"), new byte[1000]);
        }
        Files.createDirectories(destino.resolve("archivos"));
        Files.write(destino.resolve("archivos/a.docx"), new byte[500]);
        return destino;
    }

    @Test
    void sinCarpetaConfiguradaNoMuestraNada() {
        RespaldoService.Estado estado = servicio("", "").estado();
        assertThat(estado.configurado()).isFalse();
        assertThat(estado.respaldos()).isEmpty();
    }

    @Test
    void listaDelMasNuevoAlMasViejoYAvisaSiNoHayUnoReciente() throws Exception {
        Path carpeta = Files.createDirectories(tmp.resolve("respaldos"));
        respaldo(carpeta, LocalDateTime.now().minusDays(5), true);
        respaldo(carpeta, LocalDateTime.now().minusDays(3), true);
        respaldo(carpeta, LocalDateTime.now().minusHours(1), false); // falló: sin base.dump

        RespaldoService.Estado estado = servicio(carpeta.toString(), "").estado();

        assertThat(estado.configurado()).isTrue();
        assertThat(estado.puedeRespaldarAhora()).isFalse();
        assertThat(estado.respaldos()).hasSize(3);
        assertThat(estado.respaldos().get(0).completo()).isFalse();
        assertThat(estado.respaldos().get(1).tamanoBytes()).isEqualTo(1500);
        assertThat(estado.alDia()).isFalse(); // el único reciente está incompleto

        respaldo(carpeta, LocalDateTime.now().minusMinutes(5), true);
        assertThat(servicio(carpeta.toString(), "").estado().alDia()).isTrue();
    }

    @Test
    void respaldarAhoraEjecutaElScriptYDevuelveSuSalida() throws Exception {
        Path carpeta = Files.createDirectories(tmp.resolve("respaldos"));
        boolean windows =
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String nombre = LocalDateTime.now().format(NOMBRE);
        Path script;
        if (windows) {
            script = tmp.resolve("respaldo.cmd");
            Files.writeString(
                    script,
                    "@echo off\r\nmkdir \"respaldos\\" + nombre + "\"\r\necho x> \"respaldos\\" + nombre
                            + "\\base.dump\"\r\necho Respaldo listo\r\nexit /b 0\r\n");
        } else {
            script = tmp.resolve("respaldo.sh");
            Files.writeString(
                    script,
                    "mkdir -p respaldos/" + nombre + "\necho x > respaldos/" + nombre
                            + "/base.dump\necho Respaldo listo\n");
        }
        RespaldoService servicio = servicio(carpeta.toString(), script.toString());
        assertThat(servicio.estado().puedeRespaldarAhora()).isTrue();

        RespaldoService.Ejecucion ejecucion = servicio.respaldarAhora();

        assertThat(ejecucion.exito()).isTrue();
        assertThat(ejecucion.salida()).contains("Respaldo listo");
        assertThat(servicio.estado().alDia()).isTrue();
    }

    @Test
    void sinScriptNoSePuedeRespaldarDesdeLaWeb() {
        assertThatThrownBy(() -> servicio(
                                tmp.toString(), tmp.resolve("no-existe.cmd").toString())
                        .respaldarAhora())
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void recortaLaSalidaALasUltimasLineas() {
        assertThat(RespaldoService.ultimasLineas("a\nb\n\nc\nd", 2)).isEqualTo("c\nd");
    }
}
