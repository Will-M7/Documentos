package pe.estudiodocumental.solicitudes.aplicacion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pe.estudiodocumental.comun.error.LimiteExcedidoException;

class LimitePortalTest {

    @Test
    void soloCuentanLosEnviosGuardadosYCadaIpTieneSuCupo() {
        LimitePortal limite = new LimitePortal(2, 3);

        // Intentos con errores: se verifican pero no se registran, no gastan el cupo
        for (int i = 0; i < 10; i++) {
            limite.verificarEnvio("10.0.0.1");
        }
        limite.registrarEnvio("10.0.0.1");
        limite.registrarEnvio("10.0.0.1");
        assertThatThrownBy(() -> limite.verificarEnvio("10.0.0.1")).isInstanceOf(LimiteExcedidoException.class);
        assertThatCode(() -> limite.verificarEnvio("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void limitaLasConsultasDeCodigo() {
        LimitePortal limite = new LimitePortal(2, 3);
        limite.registrarConsulta("10.0.0.9");
        limite.registrarConsulta("10.0.0.9");
        limite.registrarConsulta("10.0.0.9");
        assertThatThrownBy(() -> limite.registrarConsulta("10.0.0.9")).isInstanceOf(LimiteExcedidoException.class);
    }
}
