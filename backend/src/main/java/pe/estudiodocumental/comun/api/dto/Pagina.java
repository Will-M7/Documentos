package pe.estudiodocumental.comun.api.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Respuesta paginada estándar de la API.
 *
 * @param contenido elementos de esta página.
 * @param pagina número de página, empezando en 0.
 * @param tamano elementos por página.
 * @param totalElementos total de elementos que cumplen el filtro.
 * @param totalPaginas total de páginas.
 */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> Pagina<T> de(Page<E> page, Function<E, T> convertir) {
        return new Pagina<>(
                page.getContent().stream().map(convertir).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
