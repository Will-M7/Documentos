package com.ejemplo.crudpersona.studio;

import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps the OOXML package intact; only text runs containing markers are changed. */
public final class DocumentEngine {
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)}}");

    private DocumentEngine() {}

    public static XWPFDocument open(byte[] bytes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    public static byte[] bytes(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.write(out);
            return out.toByteArray();
        }
    }

    public static Set<String> markers(XWPFDocument document) {
        Set<String> names = new LinkedHashSet<>();
        visit(document, p -> {
            Matcher matcher = TOKEN.matcher(p.getText());
            while (matcher.find()) names.add(matcher.group(1));
        });
        return names;
    }

    public static byte[] generate(byte[] template, Map<String, String> values) throws IOException {
        try (XWPFDocument document = open(template)) {
            visit(document, paragraph -> replace(paragraph, values));
            return bytes(document);
        }
    }

    private static void replace(XWPFParagraph paragraph, Map<String, String> values) {
        List<XWPFRun> runs = paragraph.getRuns();
        StringBuilder joined = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        for (XWPFRun run : runs) {
            starts.add(joined.length());
            String text = run.getText(0);
            if (text != null) joined.append(text);
        }
        Matcher matcher = TOKEN.matcher(joined);
        List<int[]> matches = new ArrayList<>();
        while (matcher.find()) if (values.containsKey(matcher.group(1))) matches.add(new int[]{matcher.start(),matcher.end()});
        for (int m=matches.size()-1;m>=0;m--) {
            int from=matches.get(m)[0], to=matches.get(m)[1];
            String key=joined.substring(from,to).replace("{{","").replace("}}","");
            int first=0,last=0;
            for (int i=0;i<runs.size();i++) {
                if (runs.get(i).getText(0)==null) continue;
                if (starts.get(i)<=from) first=i;
                if (starts.get(i)<to) last=i;
            }
            XWPFRun a=runs.get(first), b=runs.get(last);
            String left=a.getText(0), right=b.getText(0);
            if (left==null||right==null) continue;
            int localFrom=from-starts.get(first), localTo=to-starts.get(last);
            if(first==last) a.setText(left.substring(0,localFrom)+values.get(key)+left.substring(localTo),0);
            else {
                a.setText(left.substring(0,localFrom)+values.get(key),0);
                for(int i=first+1;i<last;i++)if(runs.get(i).getText(0)!=null)runs.get(i).setText("",0);
                b.setText(right.substring(localTo),0);
            }
        }
    }

    public static void visit(XWPFDocument document, java.util.function.Consumer<XWPFParagraph> action) {
        visitBody(document.getBodyElements(), action);
        for (XWPFHeader header : document.getHeaderList()) visitBody(header.getBodyElements(), action);
        for (XWPFFooter footer : document.getFooterList()) visitBody(footer.getBodyElements(), action);
    }

    private static void visitBody(List<IBodyElement> elements, java.util.function.Consumer<XWPFParagraph> action) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph p) action.accept(p);
            else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows())
                    for (XWPFTableCell cell : row.getTableCells()) visitBody(cell.getBodyElements(), action);
            }
        }
    }
}
