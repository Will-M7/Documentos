package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ContinuousDocxEditorTest {
    @Test void createsRowsAndDeletesWholeTableWithoutLeavingEmptyContent() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            doc.createParagraph().createRun().setText("Antes");ContinuousDocxEditor first=new ContinuousDocxEditor(doc);
            String html=first.html()+"<table data-borders=\"all\" data-border-width=\"1\" data-border-color=\"#8c959f\"><tr><td><p>Uno</p></td><td><p>Dos</p></td></tr></table>";
            first.apply("<div>"+html+"</div>");
            try(XWPFDocument withFirstTable=DocumentEngine.open(DocumentEngine.bytes(doc))){
                assertThat(withFirstTable.getTables()).hasSize(1);assertThat(withFirstTable.getTables().get(0).getText()).contains("Uno","Dos");
                ContinuousDocxEditor second=new ContinuousDocxEditor(withFirstTable);String withTable=second.html();
                String added=withTable.replace("</table>","<tr><td><p>Tres</p></td><td><p>Cuatro</p></td></tr></table>").replaceFirst("<table ","<table data-changed=\"true\" ");
                second.apply("<div>"+added+"</div>");
                try(XWPFDocument withSecondRow=DocumentEngine.open(DocumentEngine.bytes(withFirstTable))){
                    assertThat(withSecondRow.getTables().get(0).getNumberOfRows()).isEqualTo(2);assertThat(withSecondRow.getTables().get(0).getRow(1).getCell(1).getText()).contains("Cuatro");
                    ContinuousDocxEditor third=new ContinuousDocxEditor(withSecondRow);String removed=third.html().replaceAll("(?s)<table[^>]*>.*?</table>","");third.apply("<div>"+removed+"</div>");
                    try(XWPFDocument reopened=DocumentEngine.open(DocumentEngine.bytes(withSecondRow))){assertThat(reopened.getTables()).isEmpty();assertThat(reopened.getParagraphs()).hasSize(1);assertThat(reopened.getParagraphs().get(0).getText()).isEqualTo("Antes");}
                }
            }
        }
    }
    @Test void keepsUnchangedStructureAndSavesContinuousParagraphsAndVariables() throws Exception {
        try(XWPFDocument doc=new XWPFDocument()){
            XWPFHeader header=doc.createHeader(HeaderFooterType.DEFAULT);header.createParagraph().createRun().setText("Membrete");
            XWPFParagraph first=doc.createParagraph();first.createRun().setText("Hola ");first.createRun().setText("mundo");
            XWPFTable table=doc.createTable(1,1);table.getRow(0).getCell(0).getParagraphs().get(0).createRun().setText("Celda");
            ContinuousDocxEditor editor=new ContinuousDocxEditor(doc);
            String html=editor.html();
            html=html.replaceFirst("data-id=\"p1\"", "data-id=\"p1\" data-changed=\"true\"")
                .replace("Hola ","Hola <span class=\"variable\" data-key=\"persona_nombre\">⟦persona_nombre⟧</span> ")
                .replace(">mundo</span>", "><b><u>mundo</u></b></span>")
                .replace("<table data-table", "<p>Segundo párrafo</p><table data-table")
                .replace("Celda", "Celda <span class=\"variable\" data-key=\"otra_persona\">⟦otra_persona⟧</span>")
                .replaceFirst("(<td[^>]*><p data-id=\"[^\"]+\")", "$1 data-changed=\"true\"");
            editor.apply("<div>"+html+"</div>");
            byte[] bytes=DocumentEngine.bytes(doc);
            try(XWPFDocument reopened=DocumentEngine.open(bytes)){
                assertThat(reopened.getHeaderList().get(0).getText()).contains("Membrete");
                assertThat(reopened.getParagraphs().stream().map(XWPFParagraph::getText)).contains("Hola {{persona_nombre}} mundo","Segundo párrafo");
                assertThat(reopened.getParagraphs().get(0).getRuns().stream().anyMatch(r->r.isBold()&&r.getUnderline()==UnderlinePatterns.SINGLE&&r.text().contains("mundo"))).isTrue();
                assertThat(reopened.getBodyElements().get(1)).isInstanceOf(XWPFParagraph.class);
                assertThat(reopened.getTables().get(0).getText()).contains("Celda {{otra_persona}}");
            }
        }
    }
}
