package pe.estudiodocumental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Punto de entrada del backend de Estudio Documental. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EstudioDocumentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(EstudioDocumentalApplication.class, args);
    }
}
