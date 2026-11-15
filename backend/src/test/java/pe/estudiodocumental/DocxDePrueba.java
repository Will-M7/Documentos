package pe.estudiodocumental;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

/** Crea y lee documentos Word en memoria para las pruebas. */
public final class DocxDePrueba {

    private DocxDePrueba() {}

    /**
     * Un DOCX donde cada texto es un párrafo. Si un texto contiene "|run|", se parte ahí en varios fragmentos
     * (runs), como hace Word al cambiar el formato a mitad de una palabra.
     */
    public static byte[] crear(String... parrafos) {
        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            for (String texto : parrafos) {
                XWPFParagraph p = doc.createParagraph();
                String[] partes = texto.split("\\|run\\|");
                for (int i = 0; i < partes.length; i++) {
                    var run = p.createRun();
                    run.setText(partes[i]);
                    run.setBold(i % 2 == 1);
                }
            }
            XWPFTable tabla = doc.createTable(1, 1);
            tabla.getRow(0).getCell(0).setText("Firma: {{otorgante.nombre_completo}}");
            doc.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Todo el texto del documento (párrafos y tablas), una línea por párrafo. */
    public static String texto(byte[] docx) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String parrafos =
                    doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
            String tablas = doc.getTables().stream().map(XWPFTable::getText).collect(Collectors.joining("\n"));
            return parrafos + "\n" + tablas;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
