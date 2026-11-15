package pe.estudiodocumental.consultas.aplicacion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.estudiodocumental.comun.error.LimiteExcedidoException;

class LimiteConsultasTest {

    /** Reloj que se puede adelantar a mano. */
    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-14T10:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }
    }

    @Test
    void permiteHastaElMaximoPorMinutoYLuegoLibera() {
        RelojManual reloj = new RelojManual();
        LimiteConsultas limite = new LimiteConsultas(3, reloj);
        UUID ana = UUID.randomUUID();

        limite.registrar(ana);
        limite.registrar(ana);
        limite.registrar(ana);
        assertThatThrownBy(() -> limite.registrar(ana))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessageContaining("3 consultas por minuto");

        // Otro usuario tiene su propio límite
        assertThatCode(() -> limite.registrar(UUID.randomUUID())).doesNotThrowAnyException();

        reloj.avanzar(Duration.ofSeconds(61));
        assertThatCode(() -> limite.registrar(ana)).doesNotThrowAnyException();
    }
}
