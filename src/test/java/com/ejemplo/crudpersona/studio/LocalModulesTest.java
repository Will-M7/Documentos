package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;

class LocalModulesTest {
    @TempDir Path temp;

    @Test void transferBackupAndReopen()throws Exception{
        Path first=temp.resolve("first"),second=temp.resolve("second");DocumentStore a=new DocumentStore(first);StudioData extra=new StudioData(first);
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Hola {{nombre}}");docx=DocumentEngine.bytes(doc);}
        Template template=a.saveTemplate(new Template(0,"Saludo",docx,docx));extra.recordRevision(template.id(),docx);
        a.saveVariable(new Variable(0,template.id(),"nombre","Nombre","Texto corto","",true,1));
        a.saveKind(new Kind(0,"Carta","","",template.id(),"nombre",true));
        extra.saveMapping(new Mapping(0,template.id(),"nombre","nombres","nombre"));
        extra.saveContact(new Contact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{}"));
        extra.saveHistory(new History(0,"Carta",template.id(),1,null,"{\"nombre\":\"Ana\"}",null,null,DocumentEngine.generate(docx,Map.of("nombre","Ana")),null));
        LocalSecret secret=new LocalSecret(first);secret.save("token-privado");assertThat(secret.load()).isEqualTo("token-privado");
        Path config=temp.resolve("config.zip");StudioTransfer transfer=new StudioTransfer(a,extra);transfer.exportConfiguration(config);
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(config))){assertThat(zip.getNextEntry().getName()).isEqualTo("configuration.json");assertThat(new String(zip.readAllBytes())).doesNotContain("token-privado","12345678");}
        DocumentStore b=new DocumentStore(second);StudioData bData=new StudioData(second);
        StudioTransfer.ImportResult imported=new StudioTransfer(b,bData).importConfiguration(config,StudioTransfer.Conflict.KEEP);
        assertThat(imported.templates()).isEqualTo(1);assertThat(b.templates()).hasSize(1);assertThat(b.variables(b.templates().get(0).id())).hasSize(1);assertThat(b.kinds()).hasSize(1);assertThat(bData.mappings(b.templates().get(0).id())).hasSize(1);assertThat(bData.contacts()).isEmpty();assertThat(bData.history()).isEmpty();
        assertThat(new StudioTransfer(b,bData).importConfiguration(config,StudioTransfer.Conflict.KEEP).conflicts()).isGreaterThan(0);
        Path backup=temp.resolve("backup.dsbk");transfer.backup(backup,"contrasena-segura".toCharArray());
        assertThat(new String(Files.readAllBytes(backup),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("12345678");
        new StudioTransfer(b,bData).restore(backup,"contrasena-segura".toCharArray());
        StudioData reopened=new StudioData(second);assertThat(reopened.contacts()).hasSize(1);assertThat(reopened.history()).hasSize(1);
        assertThat(new LocalSecret(second).configured()).isFalse();
    }

    @Test void factilizaParsesDocumentedResponsesAndRejectsIncompleteData()throws Exception{
        String dni="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"12345678\",\"nombres\":\"ANA\",\"apellido_paterno\":\"PEREZ\",\"direccion_completa\":\"LIMA\"}}";
        String ruc="{\"status\":200,\"success\":true,\"message\":\"Exito\",\"data\":{\"numero\":\"20123456789\",\"nombre_o_razon_social\":\"EMPRESA\",\"direccion\":\"LIMA\"}}";
        assertThat(FactilizaClient.parse("DNI","12345678",200,dni).data()).containsEntry("nombres","ANA");
        assertThat(FactilizaClient.parse("RUC","20123456789",200,ruc).data()).containsEntry("nombre_o_razon_social","EMPRESA");
        assertThatThrownBy(()->FactilizaClient.parse("DNI","12345678",200,"{\"success\":true,\"data\":{}}" )).hasMessageContaining("incompletos");
        assertThatThrownBy(()->FactilizaClient.parse("RUC","20123456789",401,"{\"success\":false,\"message\":\"Unauthorized\"}" )).hasMessageContaining("Unauthorized");
    }

    @Test void sharedDefinitionsBindToTemplatesAndSurviveTransfer()throws Exception{
        Path source=temp.resolve("variables-source"),destination=temp.resolve("variables-destination");
        DocumentStore store=new DocumentStore(source);StudioData data=new StudioData(source);
        Definition definition=store.saveDefinition(new Definition(0,"nombre_libre","Nombre","Texto corto",""));
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Hola {{nombre_libre}}");docx=DocumentEngine.bytes(doc);}
        Template first=store.saveTemplate(new Template(0,"Primera",docx,docx));Template second=store.saveTemplate(new Template(0,"Segunda",docx,docx));
        store.bindDefinition(first.id(),definition.id(),false,1);store.bindDefinition(second.id(),definition.id(),false,1);
        Kind kind=store.saveKind(new Kind(0,"Saludo","","",first.id(),"nombre_libre",true));
        store.saveKindFieldRules(kind.id(),Map.of("nombre_libre",true),Map.of("nombre_libre","Valor local"));
        assertThat(store.definitionDependencies(definition.id())).containsExactly("Primera","Segunda");
        assertThatThrownBy(()->store.deleteDefinition(definition.id())).hasMessageContaining("Primera");
        byte[] output=DocumentEngine.generate(first.edited(),Map.of("nombre_libre","Ana"));
        try(XWPFDocument generated=DocumentEngine.open(output)){assertThat(generated.getParagraphs().get(0).getText()).isEqualTo("Hola Ana");}
        Path zip=temp.resolve("variables.zip");new StudioTransfer(store,data).exportConfiguration(zip);
        DocumentStore imported=new DocumentStore(destination);StudioData importedData=new StudioData(destination);
        new StudioTransfer(imported,importedData).importConfiguration(zip,StudioTransfer.Conflict.KEEP);
        assertThat(imported.definitions()).hasSize(1);
        assertThat(imported.definitionId(imported.variables(imported.templates().get(0).id()).get(0).id())).isEqualTo(imported.definitions().get(0).id());
        assertThat(imported.kindRequirements(imported.kinds().get(0).id())).containsEntry("nombre_libre",true);
        assertThat(imported.kindDefaults(imported.kinds().get(0).id())).containsEntry("nombre_libre","Valor local");
        assertThat(new DocumentStore(destination).definitions()).hasSize(1);
    }

    @Test void explicitGroupsSaveAndReuseIndependentContactsWithoutDuplicates()throws Exception{
        Path directory=temp.resolve("contact-groups");DocumentStore store=new DocumentStore(directory);StudioData data=new StudioData(directory);
        byte[] docx;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{nombre_a}} {{nombre_b}} {{empresa}} ");docx=DocumentEngine.bytes(doc);}
        Template template=store.saveTemplate(new Template(0,"Grupos",docx,docx));
        List<StudioData.ContactBinding> bindings=new ArrayList<>();
        String[][] rows={{"a","Persona","given","nombre_a"},{"a","Persona","number","dni_a"},{"a","Persona","address","domicilio_a"},
                {"b","Persona","given","nombre_b"},{"b","Persona","number","dni_b"},{"b","Persona","address","domicilio_b"},
                {"empresa","Empresa","given","empresa"},{"empresa","Empresa","number","ruc"}};
        for(String[] row:rows){store.saveVariable(new Variable(0,template.id(),row[3],row[3],row[2].equals("number")?(row[1].equals("Persona")?"DNI":"RUC"):"Texto corto","",false,1));bindings.add(data.saveContactBinding(new StudioData.ContactBinding(0,template.id(),row[0],row[1],row[2],row[3])));}
        Map<String,String> values=Map.of("nombre_a","Juan Pérez","dni_a","12345678","domicilio_a","Lima","nombre_b","María Ruiz","dni_b","87654321","domicilio_b","Cusco","empresa","ABC SAC","ruc","20123456789");
        Map<String,String> types=Map.of("dni_a","DNI","dni_b","DNI","ruc","RUC");
        ContactReuse.Result first=ContactReuse.save(data,bindings,values,types,(old,next)->false);
        assertThat(first.created()).isEqualTo(3);
        assertThat(ContactReuse.save(data,bindings,values,types,(old,next)->false).created()).isZero();
        assertThat(data.contacts()).hasSize(3);
        StudioData.Contact juan=data.matchingContact("Persona","DNI","12345678");
        assertThat(ContactReuse.valuesFor(juan,bindings.stream().filter(b->b.groupKey().equals("a")).toList())).containsEntry("nombre_a","Juan Pérez").containsEntry("domicilio_a","Lima");
        Map<String,String> changed=new HashMap<>(values);changed.put("domicilio_a","Arequipa");
        ContactReuse.save(data,bindings,changed,types,(old,next)->false);
        assertThat(data.matchingContact("Persona","DNI","12345678").address()).isEqualTo("Lima");
        ContactReuse.save(data,bindings,changed,types,(old,next)->true);
        assertThat(new StudioData(directory).matchingContact("Persona","DNI","12345678").address()).isEqualTo("Arequipa");
        Map<String,String> withoutDocument=new HashMap<>(values);withoutDocument.put("dni_a","");
        assertThat(ContactReuse.save(data,bindings,withoutDocument,types,(old,next)->false).skipped()).isEqualTo(1);
        assertThat(data.contacts()).hasSize(3);
        Path config=temp.resolve("groups.zip");new StudioTransfer(store,data).exportConfiguration(config);
        Path importedDirectory=temp.resolve("groups-imported");DocumentStore imported=new DocumentStore(importedDirectory);StudioData importedData=new StudioData(importedDirectory);
        new StudioTransfer(imported,importedData).importConfiguration(config,StudioTransfer.Conflict.KEEP);
        assertThat(importedData.contactBindings(imported.templates().get(0).id())).hasSize(8);
        assertThat(importedData.contacts()).isEmpty();
    }

    @Test void migratesLegacyVariablesWithoutMergingDifferentDefinitions()throws Exception{
        Path directory=temp.resolve("legacy");Files.createDirectories(directory);
        try(java.sql.Connection c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("studio.db"));java.sql.Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE template(id INTEGER PRIMARY KEY,name TEXT NOT NULL,original BLOB NOT NULL,edited BLOB NOT NULL)");
            s.executeUpdate("CREATE TABLE variable(id INTEGER PRIMARY KEY,template_id INTEGER NOT NULL,key TEXT NOT NULL,label TEXT NOT NULL,type TEXT NOT NULL,default_value TEXT,required INTEGER NOT NULL,position INTEGER NOT NULL,UNIQUE(template_id,key))");
            s.executeUpdate("INSERT INTO variable(template_id,key,label,type,default_value,required,position) VALUES(1,'dato','Nombre','Texto corto','',1,1),(2,'dato','Monto','Monto','0',0,1)");
        }
        DocumentStore migrated=new DocumentStore(directory);
        assertThat(migrated.definitions()).hasSize(2);
        assertThat(migrated.variables(1)).hasSize(1);
        assertThat(migrated.variables(2)).hasSize(1);
        assertThat(migrated.definitionId(migrated.variables(1).get(0).id())).isNotEqualTo(migrated.definitionId(migrated.variables(2).get(0).id()));
        assertThat(new DocumentStore(directory).definitions()).hasSize(2);
    }
}
