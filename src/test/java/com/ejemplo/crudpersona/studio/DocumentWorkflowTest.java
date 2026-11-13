package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentWorkflowTest {
    @TempDir Path temp;

    @Test void persistsGroupsAndTransfersTemplateAssociations() throws Exception {
        DocumentStore source=new DocumentStore(temp.resolve("source"));StudioData sourceData=new StudioData(source.directory());
        byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Hola {{nombre}}");bytes=DocumentEngine.bytes(doc);}
        Template template=source.saveTemplate(new Template(0,"Carta libre",bytes,bytes));TemplateGroup group=source.saveGroup(0,"Mis plantillas");source.moveTemplate(template.id(),group.id());
        Path packageFile=temp.resolve("config.zip");new StudioTransfer(source,sourceData).exportConfiguration(packageFile);
        DocumentStore target=new DocumentStore(temp.resolve("target"));StudioData targetData=new StudioData(target.directory());new StudioTransfer(target,targetData).importConfiguration(packageFile,StudioTransfer.Conflict.KEEP);
        assertThat(target.groups()).extracting(TemplateGroup::name).contains("Mis plantillas");
        assertThat(target.groupOf(target.templates().get(0).id())).isEqualTo(target.groups().get(0).id());
        target.deleteGroup(target.groups().get(0).id(),null);
        assertThat(target.templates()).hasSize(1);assertThat(target.groupOf(target.templates().get(0).id())).isNull();
    }

    @Test void preservesPagesHeaderFooterImageAndDocumentDefaultsAfterReopen() throws Exception {
        byte[] original;
        try(XWPFDocument doc=new XWPFDocument()){
            XWPFHeader header=doc.createHeader(HeaderFooterType.DEFAULT);
            header.createParagraph().createRun().setText("Membrete institucional");
            ByteArrayOutputStream image=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",image);
            header.createParagraph().createRun().addPicture(new ByteArrayInputStream(image.toByteArray()),XWPFDocument.PICTURE_TYPE_PNG,"logo.png",Units.toEMU(40),Units.toEMU(20));
            doc.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Pie institucional");
            XWPFParagraph first=doc.createParagraph();first.setAlignment(ParagraphAlignment.BOTH);first.createRun().setText("Primera página");
            XWPFParagraph next=doc.createParagraph();next.setPageBreak(true);next.createRun().setText("Segunda página: {{nombre}}");
            doc.createTable(1,1).getRow(0).getCell(0).setText("Tabla conservada");
            original=DocumentEngine.bytes(doc);
        }
        DocumentStore store=new DocumentStore(temp);
        Template template=store.saveTemplate(new Template(0,"Paginado",original,original));
        store.saveVariable(new Variable(0,template.id(),"nombre","Nombre","Texto corto","",false,1));
        Kind kind=store.saveKind(new Kind(0,"Paginado","","",template.id(),"nombre",true));
        store.saveKindFieldRules(kind.id(),Map.of("nombre",true),Map.of("nombre","Ejemplo"));
        DocumentStore reopened=new DocumentStore(temp);
        assertThat(reopened.kindDefaults(kind.id())).containsEntry("nombre","Ejemplo");
        byte[] generated=DocumentEngine.generate(reopened.templates().get(0).edited(),reopened.kindDefaults(kind.id()));
        try(XWPFDocument doc=DocumentEngine.open(generated)){
            assertThat(doc.getHeaderList().get(0).getParagraphs().get(0).getText()).contains("Membrete institucional");
            assertThat(doc.getFooterList().get(0).getParagraphs().get(0).getText()).contains("Pie institucional");
            assertThat(doc.getHeaderList().get(0).getParagraphs().get(1).getRuns().get(0).getEmbeddedPictures()).hasSize(1);
            assertThat(doc.getParagraphs().get(1).isPageBreak()).isTrue();
            assertThat(doc.getParagraphs().get(1).getText()).contains("Ejemplo");
            assertThat(doc.getTables().get(0).getText()).contains("Tabla conservada");
        }
    }

    @Test void importEditReopenCatalogGenerateAndPersistFiveIndependentPeople() throws Exception {
        byte[] original;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph heading = doc.createParagraph();
            heading.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun title = heading.createRun(); title.setText("Documento de ejemplo"); title.setBold(true);
            XWPFTable table = doc.createTable(1, 1);
            table.getRow(0).getCell(0).setText("Contenido de tabla conservado");
            XWPFParagraph split = doc.createParagraph();
            split.createRun().setText("Inicio {{variable_");
            split.createRun().setText("partida}} fin");
            ByteArrayOutputStream imageBytes=new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",imageBytes);
            doc.createParagraph().createRun().addPicture(new ByteArrayInputStream(imageBytes.toByteArray()),XWPFDocument.PICTURE_TYPE_PNG,"sample.png",Units.toEMU(20),Units.toEMU(20));
            original = DocumentEngine.bytes(doc);
        }
        DocumentStore store = new DocumentStore(temp);
        Template template = store.saveTemplate(new Template(0,"Ejemplo",original,original));
        try (XWPFDocument edit = DocumentEngine.open(template.edited())) {
            XWPFParagraph p = edit.createParagraph();
            for (int i=1;i<=5;i++) p.createRun().setText("Persona "+i+": {{persona"+i+"_nombre}}. ");
            p.createRun().setText("Repetido: {{persona1_nombre}}.");
            template = store.saveTemplate(new Template(template.id(),template.name(),template.original(),DocumentEngine.bytes(edit)));
        }
        for (int i=1;i<=5;i++) store.saveVariable(new Variable(0,template.id(),"persona"+i+"_nombre","Nombre "+i,"Texto corto","",true,i));
        Kind kind = store.saveKind(new Kind(0,"Cinco personas","","",template.id(),"persona1_nombre,persona2_nombre,persona3_nombre,persona4_nombre,persona5_nombre",true));

        DocumentStore reopened = new DocumentStore(temp);
        assertThat(reopened.templates()).hasSize(1);
        assertThat(reopened.templates().get(0).original()).isEqualTo(original);
        assertThat(reopened.variables(template.id())).hasSize(5);
        assertThat(reopened.kinds()).hasSize(1);
        Map<String,String> values = new HashMap<>();
        for (int i=1;i<=5;i++) values.put("persona"+i+"_nombre","Valor "+i);
        values.put("variable_partida","resuelta");
        byte[] output = DocumentEngine.generate(reopened.templates().get(0).edited(),values);
        try (XWPFDocument result = DocumentEngine.open(output)) {
            assertThat(result.getParagraphs().get(0).getRuns().get(0).isBold()).isTrue();
            assertThat(result.getParagraphs().get(0).getAlignment()).isEqualTo(ParagraphAlignment.CENTER);
            assertThat(result.getTables().get(0).getText()).contains("Contenido de tabla conservado");
            assertThat(result.getAllPictures()).hasSize(1);
            assertThat(result.getParagraphs().get(1).getText()).contains("Inicio resuelta fin");
            assertThat(result.getParagraphs().get(3).getText()).contains("Valor 1","Valor 2","Valor 3","Valor 4","Valor 5");
            assertThat(result.getParagraphs().get(3).getText()).contains("Repetido: Valor 1");
            assertThat(DocumentEngine.markers(result)).isEmpty();
        }
        byte[] pdf=DocxPdfExporter.render(output);
        PdfReader reader=new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        String pdfText=new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(pdfText).contains("Documento de ejemplo","Valor 1","Valor 5");
        assertThat(pdfText).doesNotContain("{{");
        reader.close();
        assertThat(kind.id()).isPositive();
    }
}
