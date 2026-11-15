package pe.estudiodocumental.identidad.api.dto;

import java.util.UUID;
import pe.estudiodocumental.identidad.dominio.Rol;
import pe.estudiodocumental.identidad.dominio.Usuario;

public record UsuarioResponse(UUID id, String usuario, String nombre, Rol rol) {

    public static UsuarioResponse desde(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombreUsuario(), usuario.getNombre(), usuario.getRol());
    }
}
