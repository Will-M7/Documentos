package pe.estudiodocumental.estadisticas;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Números para el panel de inicio.
 *
 * <p>Es un módulo de solo lectura que cuenta sobre tablas de varios módulos con SQL directo. Es la única excepción a
 * la regla "un módulo no lee tablas de otro": los reportes leen, nunca escriben, y así no obligan a cada módulo a
 * exponer contadores.
 */
@Service
public class EstadisticasService {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final int MESES = 6;

    private final JdbcClient jdbc;

    public EstadisticasService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Cantidad(String nombre, long cantidad) {}

    public record Resumen(
            long documentosHoy,
            long documentosMes,
            long documentosMesAnterior,
            long clientesTotal,
            long clientesNuevosMes,
            long consultasMes,
            List<Cantidad> documentosPorMes,
            List<Cantidad> plantillasDelMes,
            List<Cantidad> usuariosDelMes) {}

    @Transactional(readOnly = true)
    public Resumen resumen() {
        LocalDate hoy = LocalDate.now(ZONA);
        Instant inicioHoy = hoy.atStartOfDay(ZONA).toInstant();
        YearMonth mes = YearMonth.from(hoy);
        Instant inicioMes = mes.atDay(1).atStartOfDay(ZONA).toInstant();
        Instant inicioMesAnterior =
                mes.minusMonths(1).atDay(1).atStartOfDay(ZONA).toInstant();
        Instant inicioSerie =
                mes.minusMonths(MESES - 1).atDay(1).atStartOfDay(ZONA).toInstant();

        return new Resumen(
                contar("select count(*) from documento where creado_en >= ?", inicioHoy),
                contar("select count(*) from documento where creado_en >= ?", inicioMes),
                contar(
                        "select count(*) from documento where creado_en >= ? and creado_en < ?",
                        inicioMesAnterior,
                        inicioMes),
                contar("select count(*) from cliente where activo"),
                contar("select count(*) from cliente where activo and creado_en >= ?", inicioMes),
                contar("select count(*) from consulta_externa where creado_en >= ?", inicioMes),
                porMes(inicioSerie, mes),
                top("""
                        select plantilla_nombre as nombre, count(*) as cantidad from documento
                        where creado_en >= ? group by plantilla_nombre order by cantidad desc, nombre limit 5""", inicioMes),
                top("""
                        select u.nombre as nombre, count(*) as cantidad from documento d
                        join usuario u on u.id = d.usuario_id
                        where d.creado_en >= ? group by u.nombre order by cantidad desc, nombre limit 5""", inicioMes));
    }

    /** Los últimos meses, incluidos los que no tienen documentos (con 0). */
    private List<Cantidad> porMes(Instant desde, YearMonth actual) {
        Map<String, Long> cantidades = new LinkedHashMap<>();
        for (int i = MESES - 1; i >= 0; i--) {
            cantidades.put(actual.minusMonths(i).toString(), 0L);
        }
        jdbc.sql("""
                        select to_char(creado_en at time zone 'America/Lima', 'YYYY-MM') as mes, count(*) as cantidad
                        from documento where creado_en >= ? group by mes""")
                .param(java.sql.Timestamp.from(desde))
                .query((rs, n) -> new Cantidad(rs.getString("mes"), rs.getLong("cantidad")))
                .list()
                .forEach(c -> cantidades.computeIfPresent(c.nombre(), (k, v) -> c.cantidad()));
        List<Cantidad> serie = new ArrayList<>();
        cantidades.forEach((k, v) -> serie.add(new Cantidad(k, v)));
        return serie;
    }

    private List<Cantidad> top(String sql, Instant desde) {
        return jdbc.sql(sql)
                .param(java.sql.Timestamp.from(desde))
                .query((rs, n) -> new Cantidad(rs.getString("nombre"), rs.getLong("cantidad")))
                .list();
    }

    private long contar(String sql, Instant... desde) {
        var consulta = jdbc.sql(sql);
        for (Instant i : desde) {
            consulta = consulta.param(java.sql.Timestamp.from(i));
        }
        return consulta.query(Long.class).single();
    }
}
