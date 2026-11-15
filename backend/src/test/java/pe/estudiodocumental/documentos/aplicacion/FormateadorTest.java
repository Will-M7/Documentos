package pe.estudiodocumental.documentos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pe.estudiodocumental.variables.dominio.TipoVariable;

class FormateadorTest {

    @ParameterizedTest
    @CsvSource({
        "0, CERO",
        "1, UNO",
        "15, QUINCE",
        "21, VEINTIUNO",
        "31, TREINTA Y UNO",
        "100, CIEN",
        "101, CIENTO UNO",
        "1000, MIL",
        "1521, MIL QUINIENTOS VEINTIUNO",
        "21000, VEINTIÚN MIL",
        "31500, TREINTA Y UN MIL QUINIENTOS",
        "1000000, UN MILLÓN",
        "2500000, DOS MILLONES QUINIENTOS MIL"
    })
    void escribeEnterosEnLetras(long numero, String esperado) {
        assertThat(NumeroALetras.entero(numero)).isEqualTo(esperado);
    }

    @Test
    void escribeMontosEnSoles() {
        assertThat(NumeroALetras.soles(new BigDecimal("1500.5"))).isEqualTo("MIL QUINIENTOS CON 50/100 SOLES");
        assertThat(NumeroALetras.soles(new BigDecimal("1"))).isEqualTo("UNO CON 00/100 SOL");
    }

    @Test
    void aplicaFormatosSegunTipoYFiltro() {
        assertThat(Formateador.formatear("2026-11-14", TipoVariable.FECHA, null))
                .isEqualTo("14/11/2026");
        assertThat(Formateador.formatear("2026-11-14", TipoVariable.FECHA, "fecha_larga"))
                .isEqualTo("14 de noviembre de 2026");
        assertThat(Formateador.formatear("1500", TipoVariable.MONEDA, null)).isEqualTo("S/ 1,500.00");
        assertThat(Formateador.formatear("1500", TipoVariable.MONEDA, "letras"))
                .isEqualTo("MIL QUINIENTOS CON 00/100 SOLES");
        assertThat(Formateador.formatear("12", TipoVariable.NUMERO, "letras")).isEqualTo("DOCE");
        assertThat(Formateador.formatear("ana maría quispe", null, "mayusculas"))
                .isEqualTo("ANA MARÍA QUISPE");
        assertThat(Formateador.formatear("ANA MARÍA", null, "titulo")).isEqualTo("Ana María");
        assertThat(Formateador.formatear(null, TipoVariable.TEXTO, null)).isEmpty();
    }

    @Test
    void validaFechasYNumeros() {
        assertThat(Formateador.validar("14/11/2026", TipoVariable.FECHA)).contains("no es una fecha válida");
        assertThat(Formateador.validar("mil", TipoVariable.MONEDA)).contains("no es un número válido");
        assertThat(Formateador.validar("1,500.50", TipoVariable.MONEDA)).isNull();
    }
}
