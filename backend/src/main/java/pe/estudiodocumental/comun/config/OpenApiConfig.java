package pe.estudiodocumental.comun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación de la API, visible en /api/docs. */
@Configuration
class OpenApiConfig {

    private static final String ESQUEMA = "bearer";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Estudio Documental API")
                        .version("v1")
                        .description("API para generar documentos a partir de plantillas Word."))
                .components(new Components()
                        .addSecuritySchemes(
                                ESQUEMA,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
