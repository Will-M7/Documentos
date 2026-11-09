package com.ejemplo.crudpersona.studio;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.xwpf.usermodel.*;

import java.io.*;

/** Offline PDF renderer for common DOCX content; text remains selectable. */
public final class DocxPdfExporter {
    private DocxPdfExporter(){}
    public static byte[] render(byte[] docx)throws Exception{
        try(XWPFDocument source=DocumentEngine.open(docx);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            Rectangle page=PageSize.A4;float left=54,right=54,top=54,bottom=54;
            var section=source.getDocument().getBody().getSectPr();
            if(section!=null){
                if(section.getPgSz()!=null){float width=twips(section.getPgSz().getW()),height=twips(section.getPgSz().getH());if(width>100&&height>100)page=new Rectangle(width,height);}
                if(section.getPgMar()!=null){var m=section.getPgMar();if(m.getLeft()!=null)left=twips(m.getLeft());if(m.getRight()!=null)right=twips(m.getRight());if(m.getTop()!=null)top=twips(m.getTop());if(m.getBottom()!=null)bottom=twips(m.getBottom());}
            }
            com.lowagie.text.Document pdf=new com.lowagie.text.Document(page,left,right,top,bottom);PdfWriter.getInstance(pdf,output);pdf.open();
            for(XWPFHeader header:source.getHeaderList())for(XWPFParagraph p:header.getParagraphs())pdf.add(paragraph(p));
            for(IBodyElement element:source.getBodyElements()){
                if(element instanceof XWPFParagraph p){if(p.isPageBreak())pdf.newPage();pdf.add(paragraph(p));for(XWPFRun run:p.getRuns())for(XWPFPicture picture:run.getEmbeddedPictures())addImage(pdf,picture);}
                else if(element instanceof XWPFTable table)pdf.add(table(table));
            }
            for(XWPFFooter footer:source.getFooterList())for(XWPFParagraph p:footer.getParagraphs())pdf.add(paragraph(p));
            pdf.close();
            byte[] result=output.toByteArray();
            if(result.length<100||!new String(result,0,Math.min(5,result.length),java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"))throw new IOException("No se generó un PDF válido.");
            return result;
        }
    }
    private static float twips(Object value){return Float.parseFloat(value.toString())/20f;}
    private static Paragraph paragraph(XWPFParagraph p){Paragraph out=new Paragraph();out.setAlignment(switch(p.getAlignment()){case CENTER->Element.ALIGN_CENTER;case RIGHT->Element.ALIGN_RIGHT;case BOTH,DISTRIBUTE->Element.ALIGN_JUSTIFIED;default->Element.ALIGN_LEFT;});out.setSpacingBefore(Math.max(0,p.getSpacingBefore()/20f));out.setSpacingAfter(Math.max(0,p.getSpacingAfter()/20f));out.setIndentationLeft(Math.max(0,p.getIndentationLeft()/20f));out.setIndentationRight(Math.max(0,p.getIndentationRight()/20f));out.setFirstLineIndent(p.getIndentationFirstLine()/20f);
        for(XWPFRun run:p.getRuns()){String text=run.text();if(text==null||text.isEmpty())continue;int style=(run.isBold()?Font.BOLD:0)|(run.isItalic()?Font.ITALIC:0);float size=run.getFontSize()>0?run.getFontSize():11;Font font=FontFactory.getFont(FontFactory.HELVETICA,"Cp1252",false,size,style);out.add(new Chunk(text,font));}return out;}
    private static PdfPTable table(XWPFTable source)throws DocumentException{
        int columns=source.getRows().stream().mapToInt(row->row.getTableCells().size()).max().orElse(1);PdfPTable out=new PdfPTable(columns);out.setWidthPercentage(100);out.setSpacingBefore(5);out.setSpacingAfter(5);
        for(XWPFTableRow row:source.getRows()){int count=0;for(XWPFTableCell cell:row.getTableCells()){PdfPCell target=new PdfPCell();target.setPadding(5);for(XWPFParagraph p:cell.getParagraphs())target.addElement(paragraph(p));for(XWPFTable nested:cell.getTables())target.addElement(table(nested));out.addCell(target);count++;}while(count++<columns)out.addCell(new PdfPCell());}return out;
    }
    private static void addImage(com.lowagie.text.Document pdf,XWPFPicture picture)throws Exception{Image image=Image.getInstance(picture.getPictureData().getData());image.scaleToFit(pdf.right()-pdf.left(),pdf.top()-pdf.bottom());pdf.add(image);}
}
