package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.Mapping;
import com.ejemplo.crudpersona.studio.StudioData.ContactBinding;
import com.ejemplo.crudpersona.studio.StudioData.ApiBinding;
import com.ejemplo.crudpersona.studio.StudioData.Contact;
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

/** Portable configuration ZIP, client export ZIP and password-protected full local backup. API tokens are excluded. */
public final class StudioTransfer {
    public enum Conflict {KEEP,REPLACE}
    public record ImportResult(int templates,int kinds,int conflicts,int clients){
        public ImportResult(int templates,int kinds,int conflicts){this(templates,kinds,conflicts,0);}
    }
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final byte[] MAGIC="DSBK1".getBytes(StandardCharsets.US_ASCII),MAGIC_V2="DSBK2".getBytes(StandardCharsets.US_ASCII);
    private final DocumentStore store;private final StudioData data;
    public StudioTransfer(DocumentStore store,StudioData data){this.store=store;this.data=data;}

    public void exportConfiguration(Path destination)throws Exception{
        exportConfiguration(destination,null);
    }

    public void exportConfiguration(Path destination,Collection<Long> templateIds)throws Exception{
        List<Template> all=store.templates();
        List<Template> targets=templateIds==null?all:all.stream().filter(t->templateIds.contains(t.id())).toList();
        Set<Long> targetIds=new HashSet<>();for(Template t:targets)targetIds.add(t.id());

        Set<Long> targetGroupIds=new HashSet<>();
        for(Template t:targets)targetGroupIds.addAll(store.groupIds(t.id()));
        List<DocumentStore.TemplateGroup> targetGroups=store.groups().stream().filter(g->targetGroupIds.contains(g.id())).toList();

        if(templateIds!=null&&!targetIds.containsAll(templateIds))throw new IOException("La selección contiene plantillas inexistentes.");
        Map<Long,List<Variable>> targetVariables=new LinkedHashMap<>();
        Set<Long> targetDefIds=new LinkedHashSet<>();
        List<Definition> definitions=store.definitions();
        for(Template t:targets){
            List<Variable> variables=new ArrayList<>(store.variables(t.id()));
            Set<String> known=new HashSet<>();for(Variable v:variables){known.add(v.key());targetDefIds.add(store.definitionId(v.id()));}
            try(XWPFDocument document=DocumentEngine.open(t.edited())){
                Set<String> markers=DocumentEngine.markers(document);
                for(Definition d:definitions)if(markers.contains(d.key())&&known.add(d.key())){
                    variables.add(new Variable(-d.id(),t.id(),d.key(),d.label(),d.type(),d.defaultValue(),false,variables.size()+1));
                    targetDefIds.add(d.id());
                }
            }
            targetVariables.put(t.id(),variables);
        }
        List<Definition> targetDefs=definitions.stream().filter(d->targetDefIds.contains(d.id())).toList();
        List<ApiBinding> targetBindings=data.apiBindings().stream().filter(b->targetDefIds.contains(b.definitionId())).toList();

        ObjectNode root=JSON.createObjectNode();
        root.put("format","document-studio-config");
        root.put("version",6);
        root.set("definitions",JSON.valueToTree(targetDefs));
        root.set("apiBindings",JSON.valueToTree(targetBindings));
        root.set("groups",JSON.valueToTree(targetGroups.stream().map(g->new TemplateGroup(g.id(),g.name(),(int)targets.stream().filter(t->{try{return store.inGroup(t.id(),g.id());}catch(SQLException e){throw new IllegalStateException(e);}}).count())).toList()));
        ArrayNode templates=root.putArray("templates");
        for(Template t:targets){
            ObjectNode n=templates.addObject();
            n.put("id",t.id());
            n.put("name",t.name());
            n.put("original",Base64.getEncoder().encodeToString(t.original()));
            n.put("edited",Base64.getEncoder().encodeToString(t.edited()));
            n.set("groupIds",JSON.valueToTree(store.groupIds(t.id())));
            ArrayNode variables=n.putArray("variables");
            for(Variable v:targetVariables.get(t.id())){
                ObjectNode variable=(ObjectNode)JSON.valueToTree(v);
                variable.put("definitionId",v.id()<0?-v.id():store.definitionId(v.id()));
                variables.add(variable);
            }
            n.set("mappings",JSON.valueToTree(data.mappings(t.id())));
            n.set("contactBindings",JSON.valueToTree(data.contactBindings(t.id())));
            n.set("apiContexts",JSON.valueToTree(data.apiContexts(t.id())));
        }
        ArrayNode kinds=root.putArray("kinds");
        for(Kind k:store.kinds()){
            if(targetIds.contains(k.templateId())){
                ObjectNode kind=(ObjectNode)JSON.valueToTree(k);
                kind.set("requirements",JSON.valueToTree(store.kindRequirements(k.id())));
                kind.set("defaults",JSON.valueToTree(store.kindDefaults(k.id())));
                kinds.add(kind);
            }
        }
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(destination))){
            zip.putNextEntry(new ZipEntry("configuration.json"));
            zip.write(JSON.writeValueAsBytes(root));
            zip.closeEntry();
        }
    }

    public void exportClients(Path destination)throws Exception{
        ObjectNode root=JSON.createObjectNode();
        root.put("format","document-studio-clients");
        root.put("version",1);
        ArrayNode arr=root.putArray("clients");
        for(Contact c:data.contacts()){
            ObjectNode n=arr.addObject();
            n.put("kind",c.kind());
            n.put("given",c.given());
            n.put("family",c.family());
            n.put("documentType",c.documentType());
            n.put("number",c.number());
            n.put("address",c.address());
            n.put("representatives",c.representatives());
            n.put("extrasJson",c.extrasJson());
        }
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(destination))){
            zip.putNextEntry(new ZipEntry("clients.json"));
            zip.write(JSON.writeValueAsBytes(root));
            zip.closeEntry();
        }
    }

    public ImportResult importConfiguration(Path source,Conflict conflict)throws Exception{
        Map<String,byte[]> entriesByName=new HashMap<>();
        long total=0;
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(source))){
            ZipEntry e;
            while((e=zip.getNextEntry())!=null){
                if(e.isDirectory())continue;
                String name=e.getName();
                if(!List.of("configuration.json","clients.json").contains(name)){
                    throw new IOException("El paquete contiene una entrada desconocida: "+name);
                }
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] chunk=new byte[8192];
                int n;
                while((n=zip.read(chunk))!=-1){
                    total+=n;
                    if(total>200_000_000)throw new IOException("Paquete demasiado grande.");
                    out.write(chunk,0,n);
                }
                if(entriesByName.putIfAbsent(name,out.toByteArray())!=null)throw new IOException("Entrada ZIP duplicada: "+name);
            }
        }
        if(!entriesByName.containsKey("configuration.json")&&!entriesByName.containsKey("clients.json")){
            throw new IOException("Falta configuration.json o clients.json en el paquete.");
        }

        // Validate the clients section before mutating configuration in a combined package.
        List<Contact> clients=entriesByName.containsKey("clients.json")?readClients(entriesByName.get("clients.json")):List.of();

        int templateCount=0,kindCount=0,conflicts=0,clientCount=0;

        if(entriesByName.containsKey("configuration.json")){
            byte[] content=entriesByName.get("configuration.json");
            JsonNode root=JSON.readTree(content);
            int version=root.path("version").asInt();
            if(root==null||!"document-studio-config".equals(root.path("format").asText())||(version<1||version>6)||!root.path("templates").isArray()||!root.path("kinds").isArray()||(version>=2&&!root.path("definitions").isArray())||(version>=3&&!root.path("groups").isArray())||(version>=4&&!root.path("apiBindings").isArray()))throw new IOException("Formato de configuración inválido.");
            Set<Long> groupIds=new HashSet<>();if(version>=3)for(JsonNode g:root.path("groups"))if(g.path("id").asLong()<=0||!groupIds.add(g.path("id").asLong())||g.path("name").asText().isBlank())throw new IOException("Grupo inválido.");
            Set<Long> definitionIds=new HashSet<>();if(version>=2)for(JsonNode d:root.path("definitions")){long id=d.path("id").asLong();if(id<=0||!definitionIds.add(id)||!d.path("key").asText().matches("[a-zA-Z][a-zA-Z0-9_]*")||d.path("label").asText().isBlank()||d.path("type").asText().isBlank())throw new IOException("Definición inválida.");}
            if(version>=4)for(JsonNode b:root.path("apiBindings"))if(!definitionIds.contains(b.path("definitionId").asLong())||!b.path("path").asText().matches("(?:field:[a-z_][a-z0-9_]*|data(?:\\.[a-zA-Z_][a-zA-Z0-9_]*|\\[\\d+])+)")||b.path("service").asText().isBlank())throw new IOException("Vinculación API inválida.");
            List<JsonNode> entries=new ArrayList<>();Set<Long> ids=new HashSet<>();Map<Long,Set<String>> keys=new HashMap<>();
            for(JsonNode n:root.path("templates")){
                long id=n.path("id").asLong();
                if(id<=0||!ids.add(id)||n.path("name").asText().isBlank()||!n.path("variables").isArray()||!n.path("mappings").isArray())throw new IOException("Plantilla inválida o duplicada.");
                if(version>=3&&n.has("groupId")&&!groupIds.contains(n.path("groupId").asLong()))throw new IOException("Plantilla con grupo inexistente.");
                if(version>=5){if(!n.path("groupIds").isArray())throw new IOException("Faltan grupos de plantilla.");for(JsonNode group:n.path("groupIds"))if(!groupIds.contains(group.asLong()))throw new IOException("Plantilla con grupo inexistente.");}
                byte[] original=decode(n,"original"),edited=decode(n,"edited");
                try(XWPFDocument a=DocumentEngine.open(original);XWPFDocument b=DocumentEngine.open(edited)){}
                Set<String> templateKeys=new HashSet<>();
                for(JsonNode v:n.path("variables")){
                    String key=v.path("key").asText();
                    if(!key.matches("[a-zA-Z][a-zA-Z0-9_]*")||!templateKeys.add(key)||v.path("label").asText().isBlank())throw new IOException("Variable inválida o duplicada.");
                    if(version>=2&&!definitionIds.contains(v.path("definitionId").asLong()))throw new IOException("Variable con definición inexistente.");
                }
                for(JsonNode m:n.path("mappings"))if(!templateKeys.contains(m.path("sourceKey").asText())||!templateKeys.contains(m.path("targetKey").asText())||m.path("apiField").asText().isBlank())throw new IOException("Correspondencia inválida.");
                if(version>=2){if(!n.path("contactBindings").isArray())throw new IOException("Faltan asociaciones de contactos.");for(JsonNode b:n.path("contactBindings"))if(!templateKeys.contains(b.path("variableKey").asText())||b.path("groupKey").asText().isBlank()||!List.of("Persona","Empresa").contains(b.path("kind").asText()))throw new IOException("Asociación de contacto inválida.");}
                if(version>=6){if(!n.path("apiContexts").isArray())throw new IOException("Faltan grupos de consulta.");for(JsonNode b:n.path("apiContexts"))if(!templateKeys.contains(b.path("variableKey").asText())||b.path("groupKey").asText().isBlank()||!List.of("DNI","RUC").contains(b.path("service").asText()))throw new IOException("Grupo de consulta inválido.");}
                keys.put(id,templateKeys);entries.add(n);
            }
            for(JsonNode k:root.path("kinds")){
                long templateId=k.path("templateId").asLong();
                if(!ids.contains(templateId)||k.path("title").asText().isBlank())throw new IOException("Tipo documental sin plantilla válida.");
                for(String field:k.path("fields").asText("").split(","))if(!field.isBlank()&&!keys.get(templateId).contains(field))throw new IOException("Formulario con variable inexistente.");
            }
            Map<Long,Long> definitionMap=new HashMap<>();if(version>=2)for(JsonNode d:root.path("definitions")){Definition saved=store.importDefinition(new Definition(0,d.path("key").asText(),d.path("label").asText(),d.path("type").asText(),d.path("defaultValue").asText("")));definitionMap.put(d.path("id").asLong(),saved.id());}
            if(version>=4)for(JsonNode b:root.path("apiBindings")){long id=definitionMap.get(b.path("definitionId").asLong());ApiBinding existing=data.apiBinding(id);if(existing==null||conflict==Conflict.REPLACE)data.saveApiBinding(new ApiBinding(id,b.path("service").asText(),b.path("path").asText()));}
            Map<Long,Long> groupMap=new HashMap<>();if(version>=3)for(JsonNode g:root.path("groups")){DocumentStore.TemplateGroup existing=store.groups().stream().filter(x->x.name().equalsIgnoreCase(g.path("name").asText())).findFirst().orElse(null);DocumentStore.TemplateGroup saved=existing==null?store.saveGroup(0,g.path("name").asText()):existing;groupMap.put(g.path("id").asLong(),saved.id());}
            Map<Long,Long> idMap=new HashMap<>();Set<Long> keptTemplates=new HashSet<>();
            for(JsonNode n:entries){
                long oldId=n.path("id").asLong();
                String name=n.path("name").asText();
                Template existing=store.templates().stream().filter(t->t.name().equalsIgnoreCase(name)).findFirst().orElse(null);
                if(existing!=null){conflicts++;if(conflict==Conflict.KEEP){idMap.put(oldId,existing.id());keptTemplates.add(oldId);continue;}}
                byte[] original=decode(n,"original"),edited=decode(n,"edited");
                Template saved=existing==null?store.saveTemplate(new Template(0,name,original,edited)):store.replaceTemplate(new Template(existing.id(),name,original,edited));
                idMap.put(oldId,saved.id());
                if(existing!=null){store.clearVariables(saved.id());data.clearMappings(saved.id());data.clearContactBindings(saved.id());data.clearApiContexts(saved.id());}
                for(JsonNode v:n.path("variables")){
                    String key=v.path("key").asText();
                    Variable binding=store.saveVariable(new Variable(0,saved.id(),key,v.path("label").asText(),v.path("type").asText(),v.path("defaultValue").asText(""),v.path("required").asBoolean(),v.path("position").asInt(1)));
                    if(version>=2)store.linkDefinition(binding.id(),definitionMap.get(v.path("definitionId").asLong()));
                }
                for(JsonNode m:n.path("mappings"))data.saveMapping(new Mapping(0,saved.id(),m.path("sourceKey").asText(),m.path("apiField").asText(),m.path("targetKey").asText()));
                if(version>=2)for(JsonNode b:n.path("contactBindings"))data.saveContactBinding(new ContactBinding(0,saved.id(),b.path("groupKey").asText(),b.path("kind").asText(),b.path("field").asText(),b.path("variableKey").asText()));
                if(version>=6)for(JsonNode b:n.path("apiContexts"))data.saveApiContext(new StudioData.ApiContext(saved.id(),b.path("variableKey").asText(),b.path("groupKey").asText(),b.path("service").asText(),b.path("source").asBoolean()));
                data.recordRevision(saved.id(),edited);
                templateCount++;
            }
            if(version>=3)for(JsonNode n:entries){
                if(keptTemplates.contains(n.path("id").asLong()))continue;
                List<Long> targets=new ArrayList<>();
                if(version>=5)for(JsonNode id:n.path("groupIds")){Long target=groupMap.get(id.asLong());if(target!=null)targets.add(target);}
                else{Long target=groupMap.get(n.path("groupId").asLong());if(target!=null)targets.add(target);}
                store.setTemplateGroups(idMap.get(n.path("id").asLong()),targets);
            }
            for(JsonNode n:root.path("kinds")){
                long templateId=idMap.get(n.path("templateId").asLong());
                String title=n.path("title").asText();
                Kind existing=store.kinds().stream().filter(k->k.title().equalsIgnoreCase(title)).findFirst().orElse(null);
                if(existing!=null){conflicts++;if(conflict==Conflict.KEEP)continue;}
                Kind saved=store.saveKind(new Kind(existing==null?0:existing.id(),title,n.path("category").asText(""),n.path("description").asText(""),templateId,n.path("fields").asText(""),n.path("active").asBoolean(true)));
                if(version>=2&&n.path("requirements").isObject()){
                    Map<String,Boolean> requirements=new HashMap<>();n.path("requirements").fields().forEachRemaining(e->requirements.put(e.getKey(),e.getValue().asBoolean()));
                    Map<String,String> defaults=new HashMap<>();n.path("defaults").fields().forEachRemaining(e->defaults.put(e.getKey(),e.getValue().asText()));
                    store.saveKindFieldRules(saved.id(),requirements,defaults);
                }
                kindCount++;
            }
        }

        for(Contact incoming:clients){
            Contact existing=data.matchingContact(incoming.kind(),incoming.documentType(),incoming.number());
            if(existing!=null){conflicts++;if(conflict==Conflict.KEEP)continue;}
            data.saveContact(new Contact(existing==null?0:existing.id(),incoming.kind(),incoming.given(),incoming.family(),incoming.documentType(),incoming.number(),incoming.address(),incoming.representatives(),incoming.extrasJson()));
            clientCount++;
        }

        return new ImportResult(templateCount,kindCount,conflicts,clientCount);
    }

    private static List<Contact> readClients(byte[] content)throws IOException{
        JsonNode root=JSON.readTree(content);
        if(root==null||!"document-studio-clients".equals(root.path("format").asText())||root.path("version").asInt()!=1||!root.path("clients").isArray())throw new IOException("Formato de clients.json inválido.");
        List<Contact> clients=new ArrayList<>();
        for(JsonNode n:root.path("clients")){
            String kind=n.path("kind").asText(),given=n.path("given").asText("");
            if(!List.of("Persona","Empresa").contains(kind))throw new IOException("Tipo de cliente inválido.");
            String extras=n.path("extrasJson").asText("{}");JsonNode parsed=JSON.readTree(extras);
            if(parsed==null||!parsed.isObject())throw new IOException("Datos adicionales de cliente inválidos.");
            clients.add(new Contact(0,kind,given,n.path("family").asText(""),n.path("documentType").asText(""),n.path("number").asText(""),n.path("address").asText(""),n.path("representatives").asText(""),extras));
        }
        return clients;
    }

    private static byte[] decode(JsonNode n,String field)throws IOException{
        try{
            byte[] b=Base64.getDecoder().decode(n.path(field).asText());
            if(b.length==0||b.length>100_000_000)throw new IOException("DOCX vacío o demasiado grande.");
            return b;
        }catch(IllegalArgumentException e){
            throw new IOException("Contenido DOCX inválido.",e);
        }
    }

    public void backup(Path destination,char[] password)throws Exception{
        try{
            if(password.length<8)throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
            byte[] payload=Files.readAllBytes(data.database());
            byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(payload);
            try(OutputStream out=Files.newOutputStream(destination)){out.write(MAGIC);out.write(salt);out.write(iv);out.write(encrypted);}
        }finally{Arrays.fill(password,'\0');}
    }
    public void restore(Path source,char[] password)throws Exception{
        try{
            byte[] all=Files.readAllBytes(source);if(all.length<45)throw new IOException("Respaldo inválido.");boolean v2=Arrays.equals(Arrays.copyOfRange(all,0,5),MAGIC_V2);if(!v2&&!Arrays.equals(Arrays.copyOfRange(all,0,5),MAGIC))throw new IOException("Respaldo inválido.");
            byte[] salt=Arrays.copyOfRange(all,5,21),iv=Arrays.copyOfRange(all,21,33);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));byte[] decrypted=cipher.doFinal(all,33,all.length-33);
            byte[] db=decrypted;JsonNode restoredSecrets=JSON.createObjectNode();
            if(v2){byte[] extractedDb=null;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(decrypted))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(!List.of("studio.db","credentials.json").contains(entry.getName()))throw new IOException("Entrada no permitida en el respaldo.");byte[] bytes=zip.readAllBytes();if(bytes.length>500_000_000)throw new IOException("Respaldo demasiado grande.");if(entry.getName().equals("studio.db"))extractedDb=bytes;else restoredSecrets=JSON.readTree(bytes);}}if(extractedDb==null||restoredSecrets==null||!restoredSecrets.isObject())throw new IOException("Respaldo incompleto.");db=extractedDb;}
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
