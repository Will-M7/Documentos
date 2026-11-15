package pe.estudiodocumental.comun.config;

import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Si {@code app.web.directorio} apunta a la carpeta {@code dist} del frontend compilado, el backend sirve también la
 * web: toda la aplicación queda en un solo programa y un solo puerto (instalación sin Docker ni nginx).
 *
 * <p>Las rutas de la web (por ejemplo {@code /clientes}) que no son archivos devuelven {@code index.html}, porque las
 * resuelve React en el navegador.
 */
@Configuration
class WebEstaticaConfig implements WebMvcConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(WebEstaticaConfig.class);

    private final String directorio;

    WebEstaticaConfig(@Value("${app.web.directorio:}") String directorio) {
        this.directorio = directorio;
    }

    /** La raíz "/" no pasa por los recursos estáticos: se reenvía a index.html. */
    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        if (habilitada()) {
            registro.addViewController("/").setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        if (!habilitada()) {
            return;
        }
        Path raiz = Path.of(directorio).toAbsolutePath().normalize();
        Resource indice = new FileSystemResource(raiz.resolve("index.html"));
        LOG.info("Sirviendo la web desde {}", raiz);
        registro.addResourceHandler("/**")
                .addResourceLocations(raiz.toUri().toString())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String ruta, Resource ubicacion) throws IOException {
                        Resource archivo = ubicacion.createRelative(ruta);
                        // Solo archivos dentro de la carpeta de la web (nada de "../")
                        boolean dentro = archivo.isFile()
                                && archivo.getFile().toPath().normalize().startsWith(raiz);
                        if (dentro && archivo.isReadable()) {
                            return archivo;
                        }
                        return ruta.startsWith("api/") || ruta.startsWith("actuator/") ? null : indice;
                    }
                });
    }

    private boolean habilitada() {
        if (directorio == null || directorio.isBlank()) {
            return false;
        }
        boolean hayIndice = Path.of(directorio).resolve("index.html").toFile().isFile();
        if (!hayIndice) {
            LOG.warn("app.web.directorio={} no tiene index.html: la web no se servirá.", directorio);
        }
        return hayIndice;
    }
}
