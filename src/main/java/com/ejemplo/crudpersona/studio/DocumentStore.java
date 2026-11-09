package com.ejemplo.crudpersona.studio;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Local, independent SQLite store. DOCX originals and edited copies are durable BLOBs. */
public final class DocumentStore {
    public record Template(long id, String name, byte[] original, byte[] edited) {
        @Override public String toString() { return name; }
    }
    public record Variable(long id, long templateId, String key, String label, String type,
                           String defaultValue, boolean required, int position) {
        @Override public String toString() { return label + "  {{" + key + "}}"; }
    }
    public record Definition(long id, String key, String label, String type, String defaultValue) {
        @Override public String toString() { return label + "  {{" + key + "}} · " + type + " #" + id; }
    }
    public record Kind(long id, String title, String category, String description,
                       long templateId, String fields, boolean active) {
        @Override public String toString() { return title; }
    }

    private final Path directory;
    private final String url;

    public DocumentStore() throws Exception {
        this(defaultDirectory());
    }

    private static Path defaultDirectory() {
        String appData = System.getenv("APPDATA");
        return appData != null && !appData.isBlank()
                ? Path.of(appData, "DocumentStudio")
                : Path.of(System.getProperty("user.home"), ".document-studio");
    }

    public DocumentStore(Path directory) throws Exception {
        this.directory = directory;
        Files.createDirectories(directory);
        url = "jdbc:sqlite:" + directory.resolve("studio.db");
        Class.forName("org.sqlite.JDBC");
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS template (id INTEGER PRIMARY KEY, name TEXT NOT NULL, original BLOB NOT NULL, edited BLOB NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS variable (id INTEGER PRIMARY KEY, template_id INTEGER NOT NULL REFERENCES template(id) ON DELETE CASCADE, key TEXT NOT NULL, label TEXT NOT NULL, type TEXT NOT NULL, default_value TEXT, required INTEGER NOT NULL, position INTEGER NOT NULL, UNIQUE(template_id,key))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS variable_definition (id INTEGER PRIMARY KEY, key TEXT NOT NULL, label TEXT NOT NULL, type TEXT NOT NULL, default_value TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS kind (id INTEGER PRIMARY KEY, title TEXT NOT NULL, category TEXT, description TEXT, template_id INTEGER NOT NULL REFERENCES template(id), fields TEXT NOT NULL DEFAULT '', active INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS kind_field_rule (kind_id INTEGER NOT NULL REFERENCES kind(id) ON DELETE CASCADE, key TEXT NOT NULL, required INTEGER NOT NULL, PRIMARY KEY(kind_id,key))");
            boolean hasDefault=false;
            try(ResultSet columns=s.executeQuery("PRAGMA table_info(kind_field_rule)")){while(columns.next())if("default_value".equals(columns.getString("name")))hasDefault=true;}
            if(!hasDefault)s.executeUpdate("ALTER TABLE kind_field_rule ADD COLUMN default_value TEXT");
            s.executeUpdate("PRAGMA foreign_keys=ON");
        }
        migrateDefinitions();
    }

    public Path directory() { return directory; }
    private Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(url);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA foreign_keys=ON"); }
        return c;
    }

    private void migrateDefinitions() throws SQLException {
        try(Connection c=connect()) {
            boolean hasColumn=false;
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA table_info(variable)")) {
                while(r.next())if("definition_id".equals(r.getString("name")))hasColumn=true;
            }
            if(!hasColumn)try(Statement s=c.createStatement()){s.executeUpdate("ALTER TABLE variable ADD COLUMN definition_id INTEGER REFERENCES variable_definition(id)");}
            c.setAutoCommit(false);
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT id,key,label,type,default_value FROM variable WHERE definition_id IS NULL")) {
                while(r.next()){
                    long definition=findMatchingDefinition(c,r.getString("key"),r.getString("label"),r.getString("type"),r.getString("default_value"));
                    if(definition==0)definition=insertDefinition(c,r.getString("key"),r.getString("label"),r.getString("type"),r.getString("default_value"));
                    try(PreparedStatement update=c.prepareStatement("UPDATE variable SET definition_id=? WHERE id=?")){update.setLong(1,definition);update.setLong(2,r.getLong("id"));update.executeUpdate();}
                }
            }
            c.commit();
        }
    }
    private static long findMatchingDefinition(Connection c,String key,String label,String type,String value)throws SQLException {
        try(PreparedStatement s=c.prepareStatement("SELECT id FROM variable_definition WHERE key=? AND label=? AND type=? AND COALESCE(default_value,'')=COALESCE(?,'') ORDER BY id LIMIT 1")) {
            s.setString(1,key);s.setString(2,label);s.setString(3,type);s.setString(4,value);
            try(ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):0;}
        }
    }
    private static long insertDefinition(Connection c,String key,String label,String type,String value)throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT INTO variable_definition(key,label,type,default_value) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1,key);s.setString(2,label);s.setString(3,type);s.setString(4,value);s.executeUpdate();
            try(ResultSet r=s.getGeneratedKeys()){if(r.next())return r.getLong(1);throw new SQLException("No se creó la definición.");}
        }
    }
    public List<Definition> definitions()throws SQLException {
        List<Definition> out=new ArrayList<>();
        try(Connection c=connect();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM variable_definition ORDER BY label,key,id")){
            while(r.next())out.add(new Definition(r.getLong("id"),r.getString("key"),r.getString("label"),r.getString("type"),r.getString("default_value")));
        }
        return out;
    }
    public Definition saveDefinition(Definition d)throws SQLException {
        if(!d.key().matches("[a-zA-Z][a-zA-Z0-9_]*")||d.label().isBlank()||d.type().isBlank())throw new IllegalArgumentException("Definición de variable incompleta.");
        try(Connection c=connect()){
            c.setAutoCommit(false);
            try{
                if(d.id()==0){
                    try(PreparedStatement check=c.prepareStatement("SELECT id FROM variable_definition WHERE key=? LIMIT 1")){check.setString(1,d.key());try(ResultSet r=check.executeQuery()){if(r.next())throw new IllegalArgumentException("El identificador ya existe. Use otro o edite la variable existente.");}}
                    long id=insertDefinition(c,d.key(),d.label(),d.type(),d.defaultValue());c.commit();return new Definition(id,d.key(),d.label(),d.type(),d.defaultValue());
                }
                try(PreparedStatement s=c.prepareStatement("UPDATE variable_definition SET label=?,type=?,default_value=? WHERE id=? AND key=?")){
                    s.setString(1,d.label());s.setString(2,d.type());s.setString(3,d.defaultValue());s.setLong(4,d.id());s.setString(5,d.key());
                    if(s.executeUpdate()!=1)throw new IllegalArgumentException("El identificador es estable y no puede cambiarse.");
                }
                try(PreparedStatement s=c.prepareStatement("UPDATE variable SET label=?,type=?,default_value=? WHERE definition_id=?")){
                    s.setString(1,d.label());s.setString(2,d.type());s.setString(3,d.defaultValue());s.setLong(4,d.id());s.executeUpdate();
                }
                c.commit();return d;
            }catch(Exception e){c.rollback();if(e instanceof SQLException sql)throw sql;throw e;}
        }
    }
    public Definition importDefinition(Definition d)throws SQLException {
        if(!d.key().matches("[a-zA-Z][a-zA-Z0-9_]*")||d.label().isBlank()||d.type().isBlank())throw new IllegalArgumentException("Definición importada inválida.");
        try(Connection c=connect()){
            long id=findMatchingDefinition(c,d.key(),d.label(),d.type(),d.defaultValue());
            if(id==0)id=insertDefinition(c,d.key(),d.label(),d.type(),d.defaultValue());
            return new Definition(id,d.key(),d.label(),d.type(),d.defaultValue());
        }
    }
    public long definitionId(long variableId)throws SQLException {
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("SELECT definition_id FROM variable WHERE id=?")){
            s.setLong(1,variableId);try(ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):0;}
        }
    }
    public List<String> definitionDependencies(long id)throws SQLException {
        List<String> out=new ArrayList<>();
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("SELECT t.name FROM variable v JOIN template t ON t.id=v.template_id WHERE v.definition_id=? ORDER BY t.name")){
            s.setLong(1,id);try(ResultSet r=s.executeQuery()){while(r.next())out.add(r.getString(1));}
        }
        return out;
    }
    public void deleteDefinition(long id)throws SQLException {
        List<String> uses=definitionDependencies(id);if(!uses.isEmpty())throw new IllegalStateException("Variable utilizada en: "+String.join(", ",uses));
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("DELETE FROM variable_definition WHERE id=?")){s.setLong(1,id);s.executeUpdate();}
    }
    public Variable bindDefinition(long templateId,long definitionId,boolean required,int position)throws SQLException {
        Definition d=definitions().stream().filter(x->x.id()==definitionId).findFirst().orElseThrow(()->new IllegalArgumentException("Variable inexistente."));
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("INSERT INTO variable(template_id,key,label,type,default_value,required,position,definition_id) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            s.setLong(1,templateId);s.setString(2,d.key());s.setString(3,d.label());s.setString(4,d.type());s.setString(5,d.defaultValue());s.setInt(6,required?1:0);s.setInt(7,position);s.setLong(8,definitionId);s.executeUpdate();
            try(ResultSet r=s.getGeneratedKeys()){return new Variable(r.next()?r.getLong(1):0,templateId,d.key(),d.label(),d.type(),d.defaultValue(),required,position);}
        }
    }
    public void linkDefinition(long variableId,long definitionId)throws SQLException {
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("UPDATE variable SET definition_id=? WHERE id=?")){s.setLong(1,definitionId);s.setLong(2,variableId);s.executeUpdate();}
    }

    public List<Template> templates() throws SQLException {
        List<Template> result = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM template ORDER BY name")) {
            while (r.next()) result.add(new Template(r.getLong("id"), r.getString("name"), r.getBytes("original"), r.getBytes("edited")));
        }
        return result;
    }

    public Template saveTemplate(Template t) throws SQLException {
        if (t.id() == 0) {
            try (Connection c = connect(); PreparedStatement s = c.prepareStatement("INSERT INTO template(name,original,edited) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1,t.name()); s.setBytes(2,t.original()); s.setBytes(3,t.edited()); s.executeUpdate();
                try (ResultSet r = s.getGeneratedKeys()) { return new Template(r.next() ? r.getLong(1) : 0,t.name(),t.original(),t.edited()); }
            }
        }
        try (Connection c = connect(); PreparedStatement s = c.prepareStatement("UPDATE template SET name=?,edited=? WHERE id=?")) {
            s.setString(1,t.name()); s.setBytes(2,t.edited()); s.setLong(3,t.id()); s.executeUpdate();
        }
        return t;
    }

    public Template replaceTemplate(Template t)throws SQLException{
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("UPDATE template SET name=?,original=?,edited=? WHERE id=?")){
            s.setString(1,t.name());s.setBytes(2,t.original());s.setBytes(3,t.edited());s.setLong(4,t.id());s.executeUpdate();
        }
        return t;
    }

    public void clearVariables(long templateId)throws SQLException{
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("DELETE FROM variable WHERE template_id=?")){s.setLong(1,templateId);s.executeUpdate();}
    }

    public List<Variable> variables(long templateId) throws SQLException {
        List<Variable> result = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement s = c.prepareStatement("SELECT * FROM variable WHERE template_id=? ORDER BY position,id")) {
            s.setLong(1,templateId);
            try (ResultSet r = s.executeQuery()) { while (r.next()) result.add(new Variable(r.getLong("id"),templateId,r.getString("key"),r.getString("label"),r.getString("type"),r.getString("default_value"),r.getInt("required")!=0,r.getInt("position"))); }
        }
        return result;
    }

    public Variable saveVariable(Variable v) throws SQLException {
        if (v.id()==0) {
            try (Connection c=connect()) {
                c.setAutoCommit(false);
                try{
                    long definition=findMatchingDefinition(c,v.key(),v.label(),v.type(),v.defaultValue());
                    if(definition==0)definition=insertDefinition(c,v.key(),v.label(),v.type(),v.defaultValue());
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO variable(template_id,key,label,type,default_value,required,position,definition_id) VALUES(?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                        bindVariable(s,v);s.setLong(8,definition);s.executeUpdate();
                        try(ResultSet r=s.getGeneratedKeys()){Variable saved=new Variable(r.next()?r.getLong(1):0,v.templateId(),v.key(),v.label(),v.type(),v.defaultValue(),v.required(),v.position());c.commit();return saved;}
                    }
                }catch(SQLException e){c.rollback();throw e;}
            }
        }
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("UPDATE variable SET key=?,label=?,type=?,default_value=?,required=?,position=? WHERE id=?")) {
            s.setString(1,v.key());s.setString(2,v.label());s.setString(3,v.type());s.setString(4,v.defaultValue());s.setInt(5,v.required()?1:0);s.setInt(6,v.position());s.setLong(7,v.id());s.executeUpdate();
        }
        return v;
    }
    private static void bindVariable(PreparedStatement s,Variable v)throws SQLException{
        s.setLong(1,v.templateId());s.setString(2,v.key());s.setString(3,v.label());s.setString(4,v.type());s.setString(5,v.defaultValue());s.setInt(6,v.required()?1:0);s.setInt(7,v.position());
    }
    public void deleteVariable(long id)throws SQLException{
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("DELETE FROM variable WHERE id=?")){s.setLong(1,id);s.executeUpdate();}
    }

    public List<Kind> kinds() throws SQLException {
        List<Kind> result=new ArrayList<>();
        try(Connection c=connect();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM kind ORDER BY title")){
            while(r.next())result.add(new Kind(r.getLong("id"),r.getString("title"),r.getString("category"),r.getString("description"),r.getLong("template_id"),r.getString("fields"),r.getInt("active")!=0));
        }
        return result;
    }
    public Kind saveKind(Kind k)throws SQLException{
        if(k.id()==0){
            try(Connection c=connect();PreparedStatement s=c.prepareStatement("INSERT INTO kind(title,category,description,template_id,fields,active) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                bindKind(s,k);s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){return new Kind(r.next()?r.getLong(1):0,k.title(),k.category(),k.description(),k.templateId(),k.fields(),k.active());}
            }
        }
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("UPDATE kind SET title=?,category=?,description=?,template_id=?,fields=?,active=? WHERE id=?")){
            bindKind(s,k);s.setLong(7,k.id());s.executeUpdate();
        }
        return k;
    }
    private static void bindKind(PreparedStatement s,Kind k)throws SQLException{
        s.setString(1,k.title());s.setString(2,k.category());s.setString(3,k.description());s.setLong(4,k.templateId());s.setString(5,k.fields());s.setInt(6,k.active()?1:0);
    }
    public void deleteKind(long id)throws SQLException{
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("DELETE FROM kind WHERE id=?")){s.setLong(1,id);s.executeUpdate();}
    }
    public Map<String,Boolean> kindRequirements(long kindId)throws SQLException{
        Map<String,Boolean> out=new HashMap<>();
        try(Connection c=connect();PreparedStatement s=c.prepareStatement("SELECT key,required FROM kind_field_rule WHERE kind_id=?")){
            s.setLong(1,kindId);try(ResultSet r=s.executeQuery()){while(r.next())out.put(r.getString(1),r.getInt(2)!=0);}
        }
        return out;
    }
    public void saveKindRequirements(long kindId,Map<String,Boolean> choices)throws SQLException{
        saveKindFieldRules(kindId,choices,kindDefaults(kindId));
    }
    public Map<String,String> kindDefaults(long kindId)throws SQLException{
        Map<String,String> result=new HashMap<>();try(Connection c=connect();PreparedStatement s=c.prepareStatement("SELECT key,default_value FROM kind_field_rule WHERE kind_id=? AND default_value IS NOT NULL")){s.setLong(1,kindId);try(ResultSet r=s.executeQuery()){while(r.next())result.put(r.getString(1),r.getString(2));}}return result;
    }
    public void saveKindFieldRules(long kindId,Map<String,Boolean> choices,Map<String,String> defaults)throws SQLException{
        try(Connection c=connect()){
            c.setAutoCommit(false);
            try(PreparedStatement clear=c.prepareStatement("DELETE FROM kind_field_rule WHERE kind_id=?")){clear.setLong(1,kindId);clear.executeUpdate();}
            try(PreparedStatement insert=c.prepareStatement("INSERT INTO kind_field_rule(kind_id,key,required,default_value) VALUES(?,?,?,?)")){
                Set<String> keys=new LinkedHashSet<>(choices.keySet());keys.addAll(defaults.keySet());
                for(String key:keys){insert.setLong(1,kindId);insert.setString(2,key);insert.setInt(3,choices.getOrDefault(key,false)?1:0);insert.setString(4,defaults.get(key));insert.addBatch();}
                insert.executeBatch();
            }
            c.commit();
        }
    }
}
