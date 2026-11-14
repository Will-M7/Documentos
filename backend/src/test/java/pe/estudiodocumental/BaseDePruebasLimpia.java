package pe.estudiodocumental;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Antes de las pruebas de integración vacía la base de pruebas y aplica todas las migraciones desde cero, para
 * que cada ejecución empiece igual y verifique que las migraciones funcionan.
 *
 * <p>Solo se usa en pruebas, contra la base {@code estudio_documental_test}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class BaseDePruebasLimpia {

    @Bean
    FlywayMigrationStrategy limpiarYMigrar() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
