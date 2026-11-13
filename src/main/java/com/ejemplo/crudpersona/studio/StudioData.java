package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Additional local records. Connections are short lived so backups can safely replace the database. */
public final class StudioData {
    private static final ObjectMapper JSON=new ObjectMapper();
    public record Contact(long id,String kind,String given,String family,String documentType,String number,
                          String address,String representatives,String extrasJson) {
        @Override public String toString(){return ("Empresa".equals(kind)?given:given+" "+family)+" · "+number;}
    }
    public record History(long id,String title,long templateId,int templateVersion,String generatedAt,
                          String valuesJson,String docxPath,String pdfPath,byte[] docx,byte[] pdf){
        @Override public String toString(){return generatedAt+" · "+title;}
    }
    public record Mapping(long id,long templateId,String sourceKey,String apiField,String targetKey){
        @Override public String toString(){return sourceKey+" → "+apiField+" → "+targetKey;}
    }
    public record ApiBinding(long definitionId,String service,String path){}
    public record ApiContext(long templateId,String variableKey,String groupKey,String service,boolean source){
        @Override public String toString(){return groupKey+" · "+service+" · "+variableKey+(source?" (número a consultar)":"");}
    }
    public record ContactBinding(long id,long templateId,String groupKey,String kind,String field,String variableKey){
        @Override public String toString(){return groupKey+" · "+kind+" · "+field+" → {{"+variableKey+"}}";}
    }
    private final Path db;
    public StudioData(Path directory)throws SQLException{
        db=directory.resolve("studio.db");
        try(Connection c=open();Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE IF NOT EXISTS contact(id INTEGER PRIMARY KEY,kind TEXT NOT NULL,given TEXT NOT NULL,family TEXT NOT NULL DEFAULT '',document_type TEXT NOT NULL DEFAULT '',number TEXT NOT NULL DEFAULT '',address TEXT NOT NULL DEFAULT '',representatives TEXT NOT NULL DEFAULT '',extras_json TEXT NOT NULL DEFAULT '{}')");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_number ON contact(number)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS ix_contact_identity ON contact(kind,UPPER(TRIM(document_type)),TRIM(number))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS template_revision(template_id INTEGER NOT NULL,version INTEGER NOT NULL,content BLOB NOT NULL,created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(template_id,version))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS document_history(id INTEGER PRIMARY KEY,title TEXT NOT NULL,template_id INTEGER NOT NULL,template_version INTEGER NOT NULL,generated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,values_json TEXT NOT NULL,docx_path TEXT,pdf_path TEXT,docx BLOB NOT NULL,pdf BLOB)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS field_mapping(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,source_key TEXT NOT NULL,api_field TEXT NOT NULL,target_key TEXT NOT NULL,UNIQUE(template_id,source_key,target_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS variable_api_binding(definition_id INTEGER PRIMARY KEY,service TEXT NOT NULL,path TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS variable_query_context(template_id INTEGER NOT NULL,variable_key TEXT NOT NULL,group_key TEXT NOT NULL,service TEXT NOT NULL,source INTEGER NOT NULL,PRIMARY KEY(template_id,variable_key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS contact_binding(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,group_key TEXT NOT NULL,kind TEXT NOT NULL,field TEXT NOT NULL,variable_key TEXT NOT NULL,UNIQUE(template_id,group_key,field))");
        }
    }
    public Path database(){return db;}
    private Connection open()throws SQLException{Connection c=DriverManager.getConnection("jdbc:sqlite:"+db);try(Statement s=c.createStatement()){s.execute("PRAGMA foreign_keys=ON");}return c;}
    public List<Contact> contacts()throws SQLException{List<Contact> out=new ArrayList<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM contact ORDER BY kind,given,family")){while(r.next())out.add(new Contact(r.getLong("id"),r.getString("kind"),r.getString("given"),r.getString("family"),r.getString("document_type"),r.getString("number"),r.getString("address"),r.getString("representatives"),r.getString("extras_json")));}return out;}
    public synchronized Contact saveContact(Contact x)throws SQLException{
        try(Connection c=open()){
            c.setAutoCommit(false);
            try{
                Contact existing=matchingContact(c,x.kind(),x.documentType(),x.number());
                long id=existing==null?x.id():existing.id();
                String sql=id==0?"INSERT INTO contact(kind,given,family,document_type,number,address,representatives,extras_json) VALUES(?,?,?,?,?,?,?,?)":"UPDATE contact SET kind=?,given=?,family=?,document_type=?,number=?,address=?,representatives=?,extras_json=? WHERE id=?";
                try(PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                    bindContact(s,x);if(id!=0)s.setLong(9,id);s.executeUpdate();
                    if(id==0)try(ResultSet keys=s.getGeneratedKeys()){if(keys.next())id=keys.getLong(1);}
                }
                c.commit();
                return new Contact(id,x.kind(),x.given(),x.family(),x.documentType().trim().toUpperCase(Locale.ROOT),x.number().trim(),x.address(),x.representatives(),x.extrasJson());
            }catch(SQLException e){c.rollback();throw e;}
        }
    }
    /** Merge non-empty automatic input while retaining data absent from partial responses. */
    public synchronized Contact upsertContact(Contact incoming)throws SQLException{
        Contact existing=matchingContact(incoming.kind(),incoming.documentType(),incoming.number());
        return saveContact(existing==null?incoming:mergeContact(existing,incoming));
    }
    static Contact mergeContact(Contact existing,Contact incoming)throws SQLException{
        try{
            ObjectNode extras=JSON.createObjectNode();
            for(String source:List.of(existing.extrasJson(),incoming.extrasJson())){
                var node=JSON.readTree(source==null||source.isBlank()?"{}":source);
                if(node==null||!node.isObject())throw new IllegalArgumentException("Los datos adicionales deben ser un objeto JSON.");
                node.fields().forEachRemaining(e->{if(!e.getValue().isNull()&&!(e.getValue().isTextual()&&e.getValue().asText().isBlank()))extras.set(e.getKey(),e.getValue());});
            }
            var incomingExtras=JSON.readTree(incoming.extrasJson()==null||incoming.extrasJson().isBlank()?"{}":incoming.extrasJson());
            boolean surnameParts=incomingExtras.hasNonNull("apellido_paterno")||incomingExtras.hasNonNull("apellido_materno");
            boolean wholeFamily=incomingExtras.hasNonNull("apellidos");
            String family=prefer(incoming.family(),existing.family());
            if(surnameParts&&!wholeFamily){String combined=(extras.path("apellido_paterno").asText("")+" "+extras.path("apellido_materno").asText("")).trim();family=prefer(combined,family);}
            return new Contact(existing.id(),incoming.kind(),prefer(incoming.given(),existing.given()),family,incoming.documentType(),incoming.number(),prefer(incoming.address(),existing.address()),prefer(incoming.representatives(),existing.representatives()),JSON.writeValueAsString(extras));
        }catch(Exception e){throw new SQLException("Datos adicionales de cliente inválidos.",e);}
    }
    private static String prefer(String newer,String older){return newer==null||newer.isBlank()?older:newer;}
    private static void bindContact(PreparedStatement s,Contact x)throws SQLException{s.setString(1,x.kind());s.setString(2,x.given());s.setString(3,x.family());s.setString(4,x.documentType().trim().toUpperCase(Locale.ROOT));s.setString(5,x.number().trim());s.setString(6,x.address());s.setString(7,x.representatives());s.setString(8,x.extrasJson());}
    public void deleteContact(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM contact WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public int recordRevision(long templateId,byte[] content)throws SQLException{try(Connection c=open()){try(PreparedStatement q=c.prepareStatement("SELECT version,content FROM template_revision WHERE template_id=? ORDER BY version DESC LIMIT 1")){q.setLong(1,templateId);try(ResultSet r=q.executeQuery()){if(r.next()){int version=r.getInt(1);if(Arrays.equals(content,r.getBytes(2)))return version;return insertRevision(c,templateId,version+1,content);}}}return insertRevision(c,templateId,1,content);}}
    private static int insertRevision(Connection c,long id,int version,byte[] content)throws SQLException{try(PreparedStatement s=c.prepareStatement("INSERT INTO template_revision(template_id,version,content) VALUES(?,?,?)")){s.setLong(1,id);s.setInt(2,version);s.setBytes(3,content);s.executeUpdate();}return version;}
    public int version(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT COALESCE(MAX(version),0) FROM template_revision WHERE template_id=?")){s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){return r.next()?r.getInt(1):0;}}}
    public Map<Long,String> revisionDates()throws SQLException{Map<Long,String> dates=new HashMap<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT template_id,MAX(created_at) FROM template_revision GROUP BY template_id")){while(r.next())dates.put(r.getLong(1),r.getString(2));}return dates;}
    public byte[] revision(long templateId,int version)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT content FROM template_revision WHERE template_id=? AND version=?")){s.setLong(1,templateId);s.setInt(2,version);try(ResultSet r=s.executeQuery()){return r.next()?r.getBytes(1):null;}}}
    public List<History> history()throws SQLException{List<History> out=new ArrayList<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM document_history ORDER BY id DESC")){while(r.next())out.add(readHistory(r));}return out;}
    private static History readHistory(ResultSet r)throws SQLException{return new History(r.getLong("id"),r.getString("title"),r.getLong("template_id"),r.getInt("template_version"),r.getString("generated_at"),r.getString("values_json"),r.getString("docx_path"),r.getString("pdf_path"),r.getBytes("docx"),r.getBytes("pdf"));}
    public History saveHistory(History h)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO document_history(title,template_id,template_version,values_json,docx_path,pdf_path,docx,pdf) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){s.setString(1,h.title());s.setLong(2,h.templateId());s.setInt(3,h.templateVersion());s.setString(4,h.valuesJson());s.setString(5,h.docxPath());s.setString(6,h.pdfPath());s.setBytes(7,h.docx());s.setBytes(8,h.pdf());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){return new History(r.next()?r.getLong(1):0,h.title(),h.templateId(),h.templateVersion(),h.generatedAt(),h.valuesJson(),h.docxPath(),h.pdfPath(),h.docx(),h.pdf());}}}
    public void deleteHistory(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM document_history WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public void updateHistoryFiles(long id,String docxPath,String pdfPath,byte[] pdf)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE document_history SET docx_path=COALESCE(?,docx_path),pdf_path=COALESCE(?,pdf_path),pdf=COALESCE(?,pdf) WHERE id=?")){s.setString(1,docxPath);s.setString(2,pdfPath);s.setBytes(3,pdf);s.setLong(4,id);s.executeUpdate();}}
    public List<Mapping> mappings(long templateId)throws SQLException{List<Mapping> out=new ArrayList<>();try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM field_mapping WHERE template_id=? ORDER BY id")){s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new Mapping(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5)));}}return out;}
    public Mapping saveMapping(Mapping m)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO field_mapping(template_id,source_key,api_field,target_key) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){s.setLong(1,m.templateId());s.setString(2,m.sourceKey());s.setString(3,m.apiField());s.setString(4,m.targetKey());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){return new Mapping(r.next()?r.getLong(1):0,m.templateId(),m.sourceKey(),m.apiField(),m.targetKey());}}}
    public void deleteMapping(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM field_mapping WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public void clearMappings(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM field_mapping WHERE template_id=?")){s.setLong(1,templateId);s.executeUpdate();}}
    public ApiBinding apiBinding(long definitionId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT service,path FROM variable_api_binding WHERE definition_id=?")){s.setLong(1,definitionId);try(ResultSet r=s.executeQuery()){return r.next()?new ApiBinding(definitionId,r.getString(1),r.getString(2)):null;}}}
    public void saveApiBinding(ApiBinding binding)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO variable_api_binding(definition_id,service,path) VALUES(?,?,?) ON CONFLICT(definition_id) DO UPDATE SET service=excluded.service,path=excluded.path")){s.setLong(1,binding.definitionId());s.setString(2,binding.service());s.setString(3,binding.path());s.executeUpdate();}}
    public void deleteApiBinding(long definitionId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM variable_api_binding WHERE definition_id=?")){s.setLong(1,definitionId);s.executeUpdate();}}
    public List<ApiBinding> apiBindings()throws SQLException{List<ApiBinding> out=new ArrayList<>();try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT definition_id,service,path FROM variable_api_binding")){while(r.next())out.add(new ApiBinding(r.getLong(1),r.getString(2),r.getString(3)));}return out;}
    public List<ApiContext> apiContexts(long templateId)throws SQLException{List<ApiContext> out=new ArrayList<>();try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT template_id,variable_key,group_key,service,source FROM variable_query_context WHERE template_id=? ORDER BY group_key,source DESC,variable_key")){s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new ApiContext(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5)==1));}}return out;}
    public void saveApiContext(ApiContext context)throws SQLException{if(context.groupKey()==null||context.groupKey().isBlank()||context.variableKey()==null||context.variableKey().isBlank()||!List.of("DNI","RUC").contains(context.service()))throw new IllegalArgumentException("Grupo, variable o servicio inválido.");try(Connection c=open()){
        if(context.source())try(PreparedStatement check=c.prepareStatement("SELECT variable_key FROM variable_query_context WHERE template_id=? AND group_key=? AND service=? AND source=1 AND variable_key<>? LIMIT 1")){check.setLong(1,context.templateId());check.setString(2,context.groupKey().trim());check.setString(3,context.service());check.setString(4,context.variableKey());try(ResultSet r=check.executeQuery()){if(r.next())throw new IllegalArgumentException("El grupo ya tiene un número a consultar. Use otro grupo independiente.");}}
        try(PreparedStatement s=c.prepareStatement("INSERT INTO variable_query_context(template_id,variable_key,group_key,service,source) VALUES(?,?,?,?,?) ON CONFLICT(template_id,variable_key) DO UPDATE SET group_key=excluded.group_key,service=excluded.service,source=excluded.source")){s.setLong(1,context.templateId());s.setString(2,context.variableKey());s.setString(3,context.groupKey().trim());s.setString(4,context.service());s.setInt(5,context.source()?1:0);s.executeUpdate();}
    }}
    public void deleteApiContext(long templateId,String variableKey)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM variable_query_context WHERE template_id=? AND variable_key=?")){s.setLong(1,templateId);s.setString(2,variableKey);s.executeUpdate();}}
    public void clearApiContexts(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM variable_query_context WHERE template_id=?")){s.setLong(1,templateId);s.executeUpdate();}}
    public List<ContactBinding> contactBindings(long templateId)throws SQLException{
        List<ContactBinding> out=new ArrayList<>();
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM contact_binding WHERE template_id=? ORDER BY group_key,id")){
            s.setLong(1,templateId);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new ContactBinding(r.getLong("id"),r.getLong("template_id"),r.getString("group_key"),r.getString("kind"),r.getString("field"),r.getString("variable_key")));}
        }
        return out;
    }
    public ContactBinding saveContactBinding(ContactBinding b)throws SQLException{
        if(b.groupKey()==null||b.groupKey().isBlank()||!List.of("Persona","Empresa").contains(b.kind())||b.field()==null||b.field().isBlank()||b.variableKey()==null||b.variableKey().isBlank())throw new IllegalArgumentException("Asociación incompleta.");
        try(Connection c=open()){
            try(PreparedStatement check=c.prepareStatement("SELECT kind FROM contact_binding WHERE template_id=? AND group_key=? LIMIT 1")){
                check.setLong(1,b.templateId());check.setString(2,b.groupKey());try(ResultSet r=check.executeQuery()){if(r.next()&&!r.getString(1).equals(b.kind()))throw new IllegalArgumentException("El grupo ya tiene otro tipo de registro.");}
            }
            try(PreparedStatement check=c.prepareStatement("SELECT group_key,field FROM contact_binding WHERE template_id=? AND variable_key=? LIMIT 1")){
                check.setLong(1,b.templateId());check.setString(2,b.variableKey());try(ResultSet r=check.executeQuery()){if(r.next())throw new IllegalArgumentException("La variable ya está asociada a "+r.getString(1)+" / "+r.getString(2)+". Use una variable independiente para otro registro.");}
            }
            try(PreparedStatement s=c.prepareStatement("INSERT INTO contact_binding(template_id,group_key,kind,field,variable_key) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                s.setLong(1,b.templateId());s.setString(2,b.groupKey());s.setString(3,b.kind());s.setString(4,b.field());s.setString(5,b.variableKey());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){return new ContactBinding(r.next()?r.getLong(1):0,b.templateId(),b.groupKey(),b.kind(),b.field(),b.variableKey());}
            }
        }
    }
    public void deleteContactBinding(long id)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM contact_binding WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public void clearContactBindings(long templateId)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM contact_binding WHERE template_id=?")){s.setLong(1,templateId);s.executeUpdate();}}
    public Contact matchingContact(String kind,String documentType,String number)throws SQLException{
        try(Connection c=open()){return matchingContact(c,kind,documentType,number);}
    }
    private static Contact matchingContact(Connection c,String kind,String documentType,String number)throws SQLException{
        if(number==null)return null;
        try(PreparedStatement s=c.prepareStatement("SELECT * FROM contact WHERE kind=? AND UPPER(TRIM(document_type))=UPPER(?) AND TRIM(number)=? ORDER BY id LIMIT 1")){
            s.setString(1,kind);s.setString(2,documentType==null?"":documentType.trim());s.setString(3,number.trim());
            try(ResultSet r=s.executeQuery()){return r.next()?readContact(r):null;}
        }
    }
    public Contact findContactByNumber(String number)throws SQLException{
        if(number==null||number.isBlank())return null;
        try(Connection c=open();PreparedStatement s=c.prepareStatement("SELECT * FROM contact WHERE TRIM(number)=? LIMIT 1")){
            s.setString(1,number.trim());
            try(ResultSet r=s.executeQuery()){return r.next()?readContact(r):null;}
        }
    }
    private static Contact readContact(ResultSet r)throws SQLException{return new Contact(r.getLong("id"),r.getString("kind"),r.getString("given"),r.getString("family"),r.getString("document_type"),r.getString("number"),r.getString("address"),r.getString("representatives"),r.getString("extras_json"));}
}
