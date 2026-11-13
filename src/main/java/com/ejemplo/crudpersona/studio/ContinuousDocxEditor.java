package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Bridges a single editable HTML flow to the original OOXML package. Unchanged parts stay untouched. */
final class ContinuousDocxEditor {
    private static final Pattern TOKEN=Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)}}");
    private final XWPFDocument document;
    private final Map<String,XWPFParagraph> paragraphs=new LinkedHashMap<>();
    private final Map<String,XWPFTableCell> cells=new LinkedHashMap<>();
    private final Map<String,XWPFTable> tables=new LinkedHashMap<>();
    private int nextId;

    ContinuousDocxEditor(XWPFDocument document){this.document=document;}

    String html(){
        paragraphs.clear();cells.clear();tables.clear();nextId=0;
        StringBuilder out=new StringBuilder();
        for(XWPFHeader h:document.getHeaderList())for(XWPFParagraph p:h.getParagraphs())out.append(paragraph(p));
        for(IBodyElement element:document.getBodyElements()){
            if(element instanceof XWPFParagraph p)out.append(paragraph(p));
            else if(element instanceof XWPFTable t)out.append(table(t));
        }
        for(XWPFFooter f:document.getFooterList())for(XWPFParagraph p:f.getParagraphs())out.append(paragraph(p));
        if(out.length()==0)out.append("<p><br></p>");
        return out.toString();
    }

    private String table(XWPFTable table){
        String tableId="t"+(++nextId);tables.put(tableId,table);StringBuilder b=new StringBuilder("<table data-table=\"").append(tableId).append("\">");
        for(XWPFTableRow row:table.getRows()){
            b.append("<tr>");for(XWPFTableCell cell:row.getTableCells()){
                String id="c"+(++nextId);cells.put(id,cell);b.append("<td data-cell=\"").append(id).append("\">");for(XWPFParagraph p:cell.getParagraphs())b.append(paragraph(p));b.append("</td>");
            }b.append("</tr>");
        }return b.append("</table>").toString();
    }
    private String paragraph(XWPFParagraph p){
        String id="p"+(++nextId);paragraphs.put(id,p);
        StringBuilder b=new StringBuilder("<p data-id=\"").append(id).append("\" style=\"text-align:").append(switch(p.getAlignment()){case CENTER->"center";case RIGHT->"right";case BOTH,DISTRIBUTE->"justify";default->"left";}).append(";\">");
        if(p.isPageBreak())b.append("<span class=\"page-break\" contenteditable=\"false\"></span>");
        for(XWPFRun r:p.getRuns()){
            String content=r.text();if(content!=null&&!content.isEmpty()){
                b.append("<span style=\"font-family:").append(esc(r.getFontFamily()==null?"Arial":r.getFontFamily())).append(";font-size:").append(r.getFontSize()>0?r.getFontSize():12).append("pt;");
                if(r.isBold())b.append("font-weight:bold;");if(r.isItalic())b.append("font-style:italic;");
                if(r.getUnderline()!=UnderlinePatterns.NONE)b.append("text-decoration:underline;");
                if(r.getColor()!=null)b.append("color:#").append(r.getColor()).append(';');b.append("\">");
                Matcher m=TOKEN.matcher(content);int at=0;while(m.find()){b.append(esc(content.substring(at,m.start())));b.append("<span class=\"variable\" data-key=\"").append(esc(m.group(1))).append("\" contenteditable=\"false\" draggable=\"true\" title=\"{{").append(esc(m.group(1))).append("}}\">⟦").append(esc(m.group(1))).append("⟧</span>");at=m.end();}b.append(esc(content.substring(at))).append("</span>");
            }
            for(XWPFPicture picture:r.getEmbeddedPictures()){
                String mime=picture.getPictureData().getPackagePart().getContentType();
                b.append("<img src=\"data:").append(mime).append(";base64,").append(Base64.getEncoder().encodeToString(picture.getPictureData().getData())).append("\" width=\"").append(Math.max(1,Math.round(picture.getWidth()*96/72))).append("\" height=\"").append(Math.max(1,Math.round(picture.getDepth()*96/72))).append("\" alt=\"Imagen\">");
            }
        }
        if(p.getRuns().isEmpty())b.append("<br>");return b.append("</p>").toString();
    }
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}

    void apply(String xml) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        org.w3c.dom.Document dom=factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        Element editor=dom.getDocumentElement();
        List<Element> blocks=new ArrayList<>();collectBlocks(editor,blocks);
        for(int i=0;i<blocks.size();i++){
            Element block=blocks.get(i);if(!"table".equalsIgnoreCase(block.getTagName()))continue;
            XWPFTable table=tables.get(block.getAttribute("data-table"));boolean created=table==null;
            if(created){
                XWPFParagraph nextParagraph=null;XWPFTable nextTable=null;
                for(int j=i+1;j<blocks.size();j++){Element future=blocks.get(j);nextParagraph=paragraphs.get(future.getAttribute("data-id"));if(nextParagraph!=null&&nextParagraph.getBody()==document)break;nextParagraph=null;nextTable=tables.get(future.getAttribute("data-table"));if(nextTable!=null)break;}
                if(nextParagraph!=null)table=document.insertNewTbl(nextParagraph.getCTP().newCursor());else if(nextTable!=null)table=document.insertNewTbl(nextTable.getCTTbl().newCursor());else table=document.createTable();
                String id="newTable"+(++nextId);tables.put(id,table);block.setAttribute("data-table",id);
            }
            if(created||"true".equals(block.getAttribute("data-changed")))reconcileTable(table,block);
        }
        for(int i=0;i<blocks.size();i++){
            Element block=blocks.get(i);if(!"p".equalsIgnoreCase(block.getTagName()))continue;
            XWPFParagraph p=paragraphs.get(block.getAttribute("data-id"));
            if(p==null){
                XWPFParagraph next=null;XWPFTable nextTable=null;for(int j=i+1;j<blocks.size();j++){Element future=blocks.get(j);next=paragraphs.get(future.getAttribute("data-id"));if(next!=null&&next.getBody()==document)break;next=null;nextTable=tables.get(future.getAttribute("data-table"));if(nextTable!=null)break;}
                Element cellElement=ancestor(block,"td");XWPFTableCell cell=cellElement==null?null:cells.get(cellElement.getAttribute("data-cell"));
                if(cell!=null){if(next!=null&&next.getBody()==cell)p=cell.insertNewParagraph(next.getCTP().newCursor());else p=cell.addParagraph();}
                else if(next!=null)p=document.insertNewParagraph(next.getCTP().newCursor());else if(nextTable!=null)p=document.insertNewParagraph(nextTable.getCTTbl().newCursor());else p=document.createParagraph();
                String id="new"+(++nextId);paragraphs.put(id,p);block.setAttribute("data-id",id);writeParagraph(p,block);
            }else if("true".equals(block.getAttribute("data-changed")))writeParagraph(p,block);
        }
        Set<String> present=new HashSet<>();for(Element e:blocks)present.add(e.getAttribute("data-id"));
        for(var entry:new ArrayList<>(paragraphs.entrySet()))if(!present.contains(entry.getKey())){XWPFParagraph p=entry.getValue();if(p.getBody()==document){int index=document.getPosOfParagraph(p);if(index>=0)document.removeBodyElement(index);}else if(p.getBody() instanceof XWPFTableCell cell){int index=cell.getParagraphs().indexOf(p);if(index>=0)cell.removeParagraph(index);}}
        Set<String> presentTables=new HashSet<>();for(Element e:blocks)presentTables.add(e.getAttribute("data-table"));for(var entry:tables.entrySet())if(!presentTables.contains(entry.getKey())){int index=document.getBodyElements().indexOf(entry.getValue());if(index>=0)document.removeBodyElement(index);}
    }
    private void reconcileTable(XWPFTable table,Element html){
        NodeList rows=html.getElementsByTagName("tr");int wanted=Math.max(1,rows.getLength());while(table.getNumberOfRows()<wanted)table.createRow();while(table.getNumberOfRows()>wanted)table.removeRow(table.getNumberOfRows()-1);
        for(int ri=0;ri<rows.getLength();ri++){
            Element row=(Element)rows.item(ri);List<Element> htmlCells=directChildren(row,"td");XWPFTableRow target=table.getRow(ri);int columns=Math.max(1,htmlCells.size());while(target.getTableCells().size()<columns)target.addNewTableCell();while(target.getTableCells().size()>columns)target.removeCell(target.getTableCells().size()-1);
            for(int ci=0;ci<htmlCells.size();ci++){
                Element htmlCell=htmlCells.get(ci);XWPFTableCell cell=target.getCell(ci);String id=htmlCell.getAttribute("data-cell");if(!cells.containsKey(id)){id="newCell"+(++nextId);htmlCell.setAttribute("data-cell",id);cells.put(id,cell);}
                List<Element> paragraphsInCell=directChildren(htmlCell,"p");if(!paragraphsInCell.isEmpty()&&paragraphsInCell.get(0).getAttribute("data-id").isBlank()&&!cell.getParagraphs().isEmpty()){Element first=paragraphsInCell.get(0);String paragraphId="new"+(++nextId);first.setAttribute("data-id",paragraphId);first.setAttribute("data-changed","true");paragraphs.put(paragraphId,cell.getParagraphs().get(0));}
            }
        }
        if(html.hasAttribute("data-borders")){String mode=html.getAttribute("data-borders"),color=html.getAttribute("data-border-color").replace("#","");if(!color.matches("[0-9a-fA-F]{6}"))color="8C959F";int size=(int)Math.max(2,Math.min(96,measure(html.getAttribute("data-border-width"),1)*8));XWPFTable.XWPFBorderType line=switch(html.getAttribute("data-border-style")){case "double"->XWPFTable.XWPFBorderType.DOUBLE;case "dashed"->XWPFTable.XWPFBorderType.DASHED;default->XWPFTable.XWPFBorderType.SINGLE;},none=XWPFTable.XWPFBorderType.NONE;boolean outer=mode.equals("all")||mode.equals("outer"),inner=mode.equals("all")||mode.equals("inner");table.setTopBorder(outer?line:none,size,0,color);table.setBottomBorder(outer?line:none,size,0,color);table.setLeftBorder(outer?line:none,size,0,color);table.setRightBorder(outer?line:none,size,0,color);table.setInsideHBorder(inner?line:none,size,0,color);table.setInsideVBorder(inner?line:none,size,0,color);}
        table.setWidth("100%");
    }
    private static List<Element> directChildren(Element parent,String tag){List<Element> out=new ArrayList<>();NodeList nodes=parent.getChildNodes();for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element e&&e.getTagName().equalsIgnoreCase(tag))out.add(e);return out;}
    private static Element ancestor(Element from,String tag){Node node=from.getParentNode();while(node instanceof Element e){if(e.getTagName().equalsIgnoreCase(tag))return e;node=e.getParentNode();}return null;}
    private static void collectBlocks(Element parent,List<Element> out){
        NodeList children=parent.getChildNodes();for(int i=0;i<children.getLength();i++)if(children.item(i) instanceof Element e){String tag=e.getTagName().toLowerCase();if(tag.equals("p")||tag.equals("div")){out.add(e);}else if(tag.equals("table")){out.add(e);collectBlocks(e,out);}else collectBlocks(e,out);}
    }
    private static void writeParagraph(XWPFParagraph p,Element e) throws Exception {
        String align=e.getAttribute("style").toLowerCase();if(align.contains("text-align:center"))p.setAlignment(ParagraphAlignment.CENTER);else if(align.contains("text-align:right"))p.setAlignment(ParagraphAlignment.RIGHT);else if(align.contains("text-align:justify"))p.setAlignment(ParagraphAlignment.BOTH);else p.setAlignment(ParagraphAlignment.LEFT);
        while(!p.getRuns().isEmpty())p.removeRun(0);
        p.setPageBreak(hasPageBreak(e));
        appendNodes(p,e,new Style());
    }
    private static boolean hasPageBreak(Element e){NodeList spans=e.getElementsByTagName("span");for(int i=0;i<spans.getLength();i++)if(spans.item(i) instanceof Element s&&s.getAttribute("class").contains("page-break"))return true;return false;}
    private record Style(String font,int size,boolean bold,boolean italic,boolean underline,String color,String highlight){Style(){this("Arial",12,false,false,false,null,null);}}
    private static void appendNodes(XWPFParagraph p,Node parent,Style inherited) throws Exception {
        NodeList nodes=parent.getChildNodes();for(int i=0;i<nodes.getLength();i++){
            Node n=nodes.item(i);if(n.getNodeType()==Node.TEXT_NODE){String text=n.getTextContent();if(!text.isEmpty()){XWPFRun r=p.createRun();style(r,inherited);r.setText(text);}}
            else if(n instanceof Element e){String tag=e.getTagName().toLowerCase();if("variable".equals(e.getAttribute("class"))){XWPFRun r=p.createRun();style(r,inherited);r.setText("{{"+e.getAttribute("data-key")+"}}");}
                else if(tag.equals("br")){p.createRun().addBreak();}
                else if(tag.equals("img")){String src=e.getAttribute("src");int comma=src.indexOf(",");if(src.startsWith("data:")&&comma>0){String mime=src.substring(5,comma).toLowerCase();int type=mime.contains("png")?XWPFDocument.PICTURE_TYPE_PNG:XWPFDocument.PICTURE_TYPE_JPEG;byte[] bytes=Base64.getDecoder().decode(src.substring(comma+1));double width=measure(e.getAttribute("width"),240),height=measure(e.getAttribute("height"),160);try(InputStream in=new ByteArrayInputStream(bytes)){p.createRun().addPicture(in,type,"editor-image",Units.toEMU(width*72/96),Units.toEMU(height*72/96));}}}
                else if(!"page-break".equals(e.getAttribute("class")))appendNodes(p,e,derive(inherited,e));
            }
        }
    }
    private static double measure(String text,double fallback){try{return Math.max(1,Double.parseDouble(text.replaceAll("[^0-9.]","")));}catch(Exception ignored){return fallback;}}
    private static Style derive(Style base,Element e){String css=e.getAttribute("style").toLowerCase();String font=base.font();int size=base.size();String color=base.color(),highlight=base.highlight();boolean bold=base.bold()||e.getTagName().equalsIgnoreCase("b")||css.contains("font-weight:bold"),italic=base.italic()||e.getTagName().equalsIgnoreCase("i")||css.contains("font-style:italic"),underline=base.underline()||e.getTagName().equalsIgnoreCase("u")||css.contains("text-decoration:underline");Matcher m=Pattern.compile("font-family:\\s*([^;]+)").matcher(css);if(m.find())font=m.group(1).replace("'","").replace("\"","").trim();m=Pattern.compile("font-size:\\s*(\\d+)").matcher(css);if(m.find())size=Integer.parseInt(m.group(1));m=Pattern.compile("(?:^|;)\\s*color:\\s*(#[0-9a-f]{6})").matcher(css);if(m.find())color=m.group(1).substring(1);m=Pattern.compile("background-color:\\s*(#[0-9a-f]{6})").matcher(css);if(m.find())highlight=m.group(1).substring(1);if(e.hasAttribute("color"))color=e.getAttribute("color").replace("#","");if(e.hasAttribute("face"))font=e.getAttribute("face");return new Style(font,size,bold,italic,underline,color,highlight);}
    private static void style(XWPFRun r,Style s){r.setFontFamily(s.font());r.setFontSize(s.size());r.setBold(s.bold());r.setItalic(s.italic());if(s.underline())r.setUnderline(UnderlinePatterns.SINGLE);if(s.color()!=null)r.setColor(s.color());if(s.highlight()!=null){var props=r.getCTR().isSetRPr()?r.getCTR().getRPr():r.getCTR().addNewRPr();props.addNewShd().setFill(s.highlight());}}
}
