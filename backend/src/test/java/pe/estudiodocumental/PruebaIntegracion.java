package pe.estudiodocumental;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pe.estudiodocumental.identidad.dominio.Rol;
import pe.estudiodocumental.identidad.dominio.Usuario;
import pe.estudiodocumental.identidad.infraestructura.UsuarioRepository;

/**
 * Base de las pruebas de integración: levanta el backend completo contra {@code estudio_documental_test} y da
 * tokens listos para un ADMIN y un OPERADOR.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BaseDePruebasLimpia.class)
public abstract class PruebaIntegracion {

    protected static final String CLAVE_ADMIN = "clave-de-prueba-123";
    protected static final String CLAVE_OPERADOR = "operador-prueba-123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected String tokenAdmin() throws Exception {
        return token("admin", CLAVE_ADMIN);
    }

    protected String tokenOperador() throws Exception {
        if (usuarios.findByNombreUsuario("operador").isEmpty()) {
            usuarios.save(new Usuario(
                    "operador", "Operador de Prueba", passwordEncoder.encode(CLAVE_OPERADOR), Rol.OPERADOR));
        }
        return token("operador", CLAVE_OPERADOR);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    private String token(String usuario, String clave) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"%s\",\"clave\":\"%s\"}".formatted(usuario, clave)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
