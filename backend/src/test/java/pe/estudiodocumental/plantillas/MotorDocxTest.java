package pe.estudiodocumental.plantillas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pe.estudiodocumental.DocxDePrueba;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.plantillas.dominio.Marca;
import pe.estudiodocumental.plantillas.infraestructura.MotorDocx;

class MotorDocxTest {

    private final MotorDocx motor = new MotorDocx();

    @Test
    void detectaMarcasAunqueWordLasParta() {
        byte[] docx = DocxDePrueba.crear(
                "Yo, {{otorg|run|ante.nombres}}, con DNI {{ otorgante.numero_documento }},",
                "en {{lugar}} a {{fecha | fecha_larga}}, otorgo a {{APODERADO.nombres}}.");

        assertThat(motor.detectar(docx))
                .containsExactly(
                        new Marca("otorgante", "nombres", null),
                        new Marca("otorgante", "numero_documento", null),
                        new Marca(null, "lugar", null),
                        new Marca(null, "fecha", "fecha_larga"),
                        new Marca("apoderado", "nombres", null),
                        new Marca("otorgante", "nombre_completo", null));
    }

    @Test
    void rellenaConservandoElTextoAlrededorYEnTablas() {
        byte[] docx = DocxDePrueba.crear("Yo, {{otorg|run|ante.nombres}}, firmo en {{lugar}}.", "Monto: {{monto}}");

        byte[] resultado = motor.rellenar(docx, m -> switch (m.campo()) {
            case "nombres" -> "ANA";
            case "lugar" -> "Cusco";
            case "nombre_completo" -> "Ana Quispe";
            default -> "linea 1\nlinea 2";
        });

        String texto = DocxDePrueba.texto(resultado);
        assertThat(texto).contains("Yo, ANA, firmo en Cusco.");
        assertThat(texto).contains("Monto: linea 1\nlinea 2");
        assertThat(texto).contains("Firma: Ana Quispe");
        assertThat(texto).doesNotContain("{{");
    }

    @Test
    void rechazaArchivosQueNoSonWord() {
        assertThatThrownBy(() -> motor.detectar("hola".getBytes()))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining(".docx");
    }
}
