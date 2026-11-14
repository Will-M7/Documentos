package pe.estudiodocumental.plantillas.infraestructura;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import pe.estudiodocumental.comun.error.ReglaNegocioException;
import pe.estudiodocumental.plantillas.dominio.Marca;

/**
 * Lee y rellena plantillas DOCX con Apache POI.
 *
 * <p>Word suele partir un texto como {@code {{nombres}}} en varios fragmentos internos ("runs") con formatos
 * distintos. Por eso la búsqueda se hace sobre el texto completo del párrafo y el reemplazo conserva el formato del
 * primer fragmento de cada marca. Recorre cuerpo, tablas (incluso anidadas), encabezados y pies de página.
 */
@Component
public class MotorDocx {

    /** Marcas en orden de aparición y sin repetir. */
    public List<Marca> detectar(byte[] docx) {
        Set<Marca> marcas = new LinkedHashSet<>();
        try (XWPFDocument documento = abrir(docx)) {
            recorrer(documento, parrafo -> {
                Matcher m = Marca.PATRON.matcher(parrafo.getText());
                while (m.find()) {
                    marcas.add(Marca.desde(m));
                }
            });
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo leer el archivo Word.");
        }
        return List.copyOf(marcas);
    }

    /** Devuelve una copia del DOCX con cada marca reemplazada por {@code valor.apply(marca)}. */
    public byte[] rellenar(byte[] docx, Function<Marca, String> valor) {
        try (XWPFDocument documento = abrir(docx);
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            recorrer(documento, parrafo -> reemplazar(parrafo, valor));
            documento.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new ReglaNegocioException("No se pudo generar el documento Word.");
        }
    }

    private static XWPFDocument abrir(byte[] docx) throws IOException {
        if (docx == null || docx.length < 4 || docx[0] != 'P' || docx[1] != 'K') {
            throw new ReglaNegocioException("El archivo no es un documento Word (.docx).");
        }
        try {
            return new XWPFDocument(new ByteArrayInputStream(docx));
        } catch (Exception e) {
            throw new ReglaNegocioException("El archivo no es un documento Word (.docx) válido.");
        }
    }

    private static void reemplazar(XWPFParagraph parrafo, Function<Marca, String> valor) {
        List<XWPFRun> runs = parrafo.getRuns();
        StringBuilder texto = new StringBuilder();
        List<Integer> inicios = new ArrayList<>();
        for (XWPFRun run : runs) {
            inicios.add(texto.length());
            String t = run.getText(0);
            if (t != null) {
                texto.append(t);
            }
        }
        Matcher m = Marca.PATRON.matcher(texto);
        List<int[]> rangos = new ArrayList<>();
        List<Marca> marcas = new ArrayList<>();
        while (m.find()) {
            rangos.add(new int[] {m.start(), m.end()});
            marcas.add(Marca.desde(m));
        }
        // De atrás hacia adelante para no mover las posiciones de las marcas anteriores
        for (int i = rangos.size() - 1; i >= 0; i--) {
            int desde = rangos.get(i)[0];
            int hasta = rangos.get(i)[1];
            int primero = -1;
            int ultimo = -1;
            for (int r = 0; r < runs.size(); r++) {
                if (runs.get(r).getText(0) == null) {
                    continue;
                }
                if (inicios.get(r) <= desde) {
                    primero = r;
                }
                if (inicios.get(r) < hasta) {
                    ultimo = r;
                }
            }
            if (primero < 0 || ultimo < 0) {
                continue;
            }
            XWPFRun a = runs.get(primero);
            XWPFRun b = runs.get(ultimo);
            String izquierda = a.getText(0).substring(0, desde - inicios.get(primero));
            String derecha = b.getText(0).substring(hasta - inicios.get(ultimo));
            String nuevo = valor.apply(marcas.get(i));
            if (primero == ultimo) {
                escribir(a, izquierda + nuevo + derecha);
            } else {
                escribir(a, izquierda + nuevo);
                for (int r = primero + 1; r < ultimo; r++) {
                    if (runs.get(r).getText(0) != null) {
                        runs.get(r).setText("", 0);
                    }
                }
                b.setText(derecha, 0);
            }
        }
    }

    /** Escribe el texto; los saltos de línea se convierten en saltos de Word dentro del mismo párrafo. */
    private static void escribir(XWPFRun run, String texto) {
        String[] lineas = texto.split("\n", -1);
        run.setText(lineas[0], 0);
        for (int i = 1; i < lineas.length; i++) {
            run.addBreak();
            run.setText(lineas[i]);
        }
    }

    private static void recorrer(XWPFDocument documento, Consumer<XWPFParagraph> accion) {
        recorrerCuerpo(documento.getBodyElements(), accion);
        for (XWPFHeader encabezado : documento.getHeaderList()) {
            recorrerCuerpo(encabezado.getBodyElements(), accion);
        }
        for (XWPFFooter pie : documento.getFooterList()) {
            recorrerCuerpo(pie.getBodyElements(), accion);
        }
    }

    private static void recorrerCuerpo(List<IBodyElement> elementos, Consumer<XWPFParagraph> accion) {
        for (IBodyElement elemento : elementos) {
            if (elemento instanceof XWPFParagraph parrafo) {
                accion.accept(parrafo);
            } else if (elemento instanceof XWPFTable tabla) {
                for (XWPFTableRow fila : tabla.getRows()) {
                    for (XWPFTableCell celda : fila.getTableCells()) {
                        recorrerCuerpo(celda.getBodyElements(), accion);
                    }
                }
            }
        }
    }
}
