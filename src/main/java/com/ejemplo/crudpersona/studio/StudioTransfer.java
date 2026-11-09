package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.Mapping;
import com.ejemplo.crudpersona.studio.StudioData.ContactBinding;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.zip.*;

/** Portable configuration ZIP and password-protected full local backup. API tokens are excluded. */
public final class StudioTransfer {
    public enum Conflict {KEEP,REPLACE}
    public record ImportResult(int templates,int kinds,int conflicts){}
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final byte[] MAGIC="DSBK1".getBytes(StandardCharsets.US_ASCII);
    private final DocumentStore store;private final StudioData data;
    public StudioTransfer(DocumentStore store,StudioData data){this.store=store;this.data=data;}

    public void exportConfiguration(Path destination)throws Exception{
        ObjectNode root=JSON.createObjectNode();root.put("format","document-studio-config");root.put("version",2);root.set("definitions",JSON.valueToTree(store.definitions()));ArrayNode templates=root.putArray("templates");
        for(Template t:store.templates()){
            ObjectNode n=templates.addObject();n.put("id",t.id());n.put("name",t.name());n.put("original",Base64.getEncoder().encodeToString(t.original()));n.put("edited",Base64.getEncoder().encodeToString(t.edited()));
            ArrayNode variables=n.putArray("variables");for(Variable v:store.variables(t.id())){ObjectNode variable=(ObjectNode)JSON.valueToTree(v);variable.put("definitionId",store.definitionId(v.id()));variables.add(variable);}
            n.set("mappings",JSON.valueToTree(data.mappings(t.id())));
            n.set("contactBindings",JSON.valueToTree(data.contactBindings(t.id())));
        }
        ArrayNode kinds=root.putArray("kinds");for(Kind k:store.kinds()){ObjectNode kind=(ObjectNode)JSON.valueToTree(k);kind.set("requirements",JSON.valueToTree(store.kindRequirements(k.id())));kind.set("defaults",JSON.valueToTree(store.kindDefaults(k.id())));kinds.add(kind);}
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(destination))){zip.putNextEntry(new ZipEntry("configuration.json"));zip.write(JSON.writeValueAsBytes(root));zip.closeEntry();}
    }

    public ImportResult importConfiguration(Path source,Conflict conflict)throws Exception{
        byte[] content=null;long total=0;try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(source))){ZipEntry e;while((e=zip.getNextEntry())!=null){if(e.isDirectory()||!"configuration.json".equals(e.getName()))throw new IOException("El paquete contiene una entrada desconocida.");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;while((n=zip.read(chunk))!=-1){total+=n;if(total>200_000_000)throw new IOException("Paquete demasiado grande.");out.write(chunk,0,n);}if(content!=null)throw new IOException("Paquete duplicado.");content=out.toByteArray();}}
        if(content==null)throw new IOException("Falta configuration.json.");JsonNode root=JSON.readTree(content);
        int version=root.path("version").asInt();
        if(!"document-studio-config".equals(root.path("format").asText())||(version!=1&&version!=2)||!root.path("templates").isArray()||!root.path("kinds").isArray()||(version==2&&!root.path("definitions").isArray()))throw new IOException("Formato de configuración inválido.");
        Set<Long> definitionIds=new HashSet<>();if(version==2)for(JsonNode d:root.path("definitions")){long id=d.path("id").asLong();if(id<=0||!definitionIds.add(id)||!d.path("key").asText().matches("[a-zA-Z][a-zA-Z0-9_]*")||d.path("label").asText().isBlank()||d.path("type").asText().isBlank())throw new IOException("Definición inválida.");}
        List<JsonNode> entries=new ArrayList<>();Set<Long> ids=new HashSet<>();Map<Long,Set<String>> keys=new HashMap<>();
        for(JsonNode n:root.path("templates")){
            long id=n.path("id").asLong();
            if(id<=0||!ids.add(id)||n.path("name").asText().isBlank()||!n.path("variables").isArray()||!n.path("mappings").isArray())throw new IOException("Plantilla inválida o duplicada.");
            byte[] original=decode(n,"original"),edited=decode(n,"edited");
            try(XWPFDocument a=DocumentEngine.open(original);XWPFDocument b=DocumentEngine.open(edited)){}
            Set<String> templateKeys=new HashSet<>();
            for(JsonNode v:n.path("variables")){
                String key=v.path("key").asText();
                if(!key.matches("[a-zA-Z][a-zA-Z0-9_]*")||!templateKeys.add(key)||v.path("label").asText().isBlank())throw new IOException("Variable inválida o duplicada.");
                if(version==2&&!definitionIds.contains(v.path("definitionId").asLong()))throw new IOException("Variable con definición inexistente.");
            }
            for(JsonNode m:n.path("mappings"))if(!templateKeys.contains(m.path("sourceKey").asText())||!templateKeys.contains(m.path("targetKey").asText())||m.path("apiField").asText().isBlank())throw new IOException("Correspondencia inválida.");
            if(version==2){if(!n.path("contactBindings").isArray())throw new IOException("Faltan asociaciones de contactos.");for(JsonNode b:n.path("contactBindings"))if(!templateKeys.contains(b.path("variableKey").asText())||b.path("groupKey").asText().isBlank()||!List.of("Persona","Empresa").contains(b.path("kind").asText()))throw new IOException("Asociación de contacto inválida.");}
            keys.put(id,templateKeys);entries.add(n);
        }
        for(JsonNode k:root.path("kinds")){
            long templateId=k.path("templateId").asLong();
            if(!ids.contains(templateId)||k.path("title").asText().isBlank())throw new IOException("Tipo documental sin plantilla válida.");
            for(String field:k.path("fields").asText("").split(","))if(!field.isBlank()&&!keys.get(templateId).contains(field))throw new IOException("Formulario con variable inexistente.");
        }
        Map<Long,Long> definitionMap=new HashMap<>();if(version==2)for(JsonNode d:root.path("definitions")){Definition saved=store.importDefinition(new Definition(0,d.path("key").asText(),d.path("label").asText(),d.path("type").asText(),d.path("defaultValue").asText("")));definitionMap.put(d.path("id").asLong(),saved.id());}
        Map<Long,Long> idMap=new HashMap<>();int templateCount=0,kindCount=0,conflicts=0;
        for(JsonNode n:entries){long oldId=n.path("id").asLong();String name=n.path("name").asText();Template existing=store.templates().stream().filter(t->t.name().equalsIgnoreCase(name)).findFirst().orElse(null);if(existing!=null){conflicts++;if(conflict==Conflict.KEEP){idMap.put(oldId,existing.id());continue;}}byte[] original=decode(n,"original"),edited=decode(n,"edited");Template saved=existing==null?store.saveTemplate(new Template(0,name,original,edited)):store.replaceTemplate(new Template(existing.id(),name,original,edited));idMap.put(oldId,saved.id());if(existing!=null){store.clearVariables(saved.id());data.clearMappings(saved.id());data.clearContactBindings(saved.id());}for(JsonNode v:n.path("variables")){String key=v.path("key").asText();Variable binding=store.saveVariable(new Variable(0,saved.id(),key,v.path("label").asText(),v.path("type").asText(),v.path("defaultValue").asText(""),v.path("required").asBoolean(),v.path("position").asInt(1)));if(version==2)store.linkDefinition(binding.id(),definitionMap.get(v.path("definitionId").asLong()));}for(JsonNode m:n.path("mappings"))data.saveMapping(new Mapping(0,saved.id(),m.path("sourceKey").asText(),m.path("apiField").asText(),m.path("targetKey").asText()));if(version==2)for(JsonNode b:n.path("contactBindings"))data.saveContactBinding(new ContactBinding(0,saved.id(),b.path("groupKey").asText(),b.path("kind").asText(),b.path("field").asText(),b.path("variableKey").asText()));data.recordRevision(saved.id(),edited);templateCount++;}
        for(JsonNode n:root.path("kinds")){long templateId=idMap.get(n.path("templateId").asLong());String title=n.path("title").asText();Kind existing=store.kinds().stream().filter(k->k.title().equalsIgnoreCase(title)).findFirst().orElse(null);if(existing!=null){conflicts++;if(conflict==Conflict.KEEP)continue;}Kind saved=store.saveKind(new Kind(existing==null?0:existing.id(),title,n.path("category").asText(""),n.path("description").asText(""),templateId,n.path("fields").asText(""),n.path("active").asBoolean(true)));if(version==2&&n.path("requirements").isObject()){Map<String,Boolean> requirements=new HashMap<>();n.path("requirements").fields().forEachRemaining(e->requirements.put(e.getKey(),e.getValue().asBoolean()));Map<String,String> defaults=new HashMap<>();n.path("defaults").fields().forEachRemaining(e->defaults.put(e.getKey(),e.getValue().asText()));store.saveKindFieldRules(saved.id(),requirements,defaults);}kindCount++;}
        return new ImportResult(templateCount,kindCount,conflicts);
    }
    private static byte[] decode(JsonNode n,String field)throws IOException{try{byte[] b=Base64.getDecoder().decode(n.path(field).asText());if(b.length==0||b.length>100_000_000)throw new IOException("DOCX vacío o demasiado grande.");return b;}catch(IllegalArgumentException e){throw new IOException("Contenido DOCX inválido.",e);}}

    public void backup(Path destination,char[] password)throws Exception{
        try{
            if(password.length<8)throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
            byte[] db=Files.readAllBytes(data.database());byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(db);
            try(OutputStream out=Files.newOutputStream(destination)){out.write(MAGIC);out.write(salt);out.write(iv);out.write(encrypted);}
        }finally{Arrays.fill(password,'\0');}
    }
    public void restore(Path source,char[] password)throws Exception{
        try{
            byte[] all=Files.readAllBytes(source);if(all.length<45||!Arrays.equals(Arrays.copyOfRange(all,0,5),MAGIC))throw new IOException("Respaldo inválido.");
            byte[] salt=Arrays.copyOfRange(all,5,21),iv=Arrays.copyOfRange(all,21,33);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));byte[] db=cipher.doFinal(all,33,all.length-33);
            Path temp=Files.createTempFile(data.database().getParent(),"restore-",".db");
            try{
                Files.write(temp,db);
                try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+temp);Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA integrity_check")){
                    if(!r.next()||!"ok".equalsIgnoreCase(r.getString(1)))throw new IOException("Base de datos dañada.");
                }
                try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+temp);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")){
                    Set<String> tables=new HashSet<>();while(r.next())tables.add(r.getString(1));
                    if(!tables.containsAll(Set.of("template","variable","kind","contact","template_revision","document_history","field_mapping")))throw new IOException("El respaldo no contiene todas las tablas del sistema.");
                }
                try{Files.move(temp,data.database(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,data.database(),StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temp);}
        }finally{Arrays.fill(password,'\0');}
    }
    private static SecretKeySpec key(char[] password,byte[] salt)throws Exception{PBEKeySpec spec=new PBEKeySpec(password,salt,200_000,256);byte[] material=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();spec.clearPassword();return new SecretKeySpec(material,"AES");}
}
