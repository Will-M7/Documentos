package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import com.ejemplo.crudpersona.studio.StudioData.*;
import com.fasterxml.jackson.databind.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;

import static org.assertj.core.api.Assertions.*;

class StudioTransferTest {
    @TempDir Path temp;
    private static final ObjectMapper JSON=new ObjectMapper();

    @Test void selectivePackagesContainOnlyRelatedGraphAndKeepDocxBytesIntact()throws Exception{
        DocumentStore source=new DocumentStore(temp.resolve("source"));StudioData data=new StudioData(source.directory());
        Template selected=template(source,"Seleccionada","{{dni}} {{nombre}} {{ciudad}}");Template other=template(source,"Otra","{{otro}}");
        TemplateGroup shared=source.saveGroup(0,"Compartido"),exclusive=source.saveGroup(0,"Exclusivo");source.setTemplateGroups(selected.id(),List.of(shared.id()));source.setTemplateGroups(other.id(),List.of(shared.id(),exclusive.id()));
        Definition dni=source.saveDefinition(new Definition(0,"dni","DNI","DNI",""));Definition name=source.saveDefinition(new Definition(0,"nombre","Nombre","Texto corto",""));Definition city=source.saveDefinition(new Definition(0,"ciudad","Ciudad","Texto corto",""));Definition unrelated=source.saveDefinition(new Definition(0,"otro","Otro","Texto corto",""));
        source.bindDefinition(selected.id(),dni.id(),true,1);source.bindDefinition(selected.id(),name.id(),false,2);source.bindDefinition(other.id(),unrelated.id(),false,1);
        // Ciudad occurs in OOXML without an explicit variable row.
        data.saveApiBinding(new ApiBinding(name.id(),"DNI","field:nombres"));data.saveApiBinding(new ApiBinding(city.id(),"DNI","field:distrito"));data.saveApiBinding(new ApiBinding(unrelated.id(),"RUC","field:razon_social"));
        data.saveApiContext(new ApiContext(selected.id(),"dni","cliente","DNI",true));data.saveApiContext(new ApiContext(selected.id(),"nombre","cliente","DNI",false));data.saveContactBinding(new ContactBinding(0,selected.id(),"cliente","Persona","given","nombre"));
        data.saveMapping(new Mapping(0,selected.id(),"dni","nombres","nombre"));
        Kind kind=source.saveKind(new Kind(0,"Carta","Categoría","Descripción",selected.id(),"dni,nombre,ciudad",true));source.saveKindFieldRules(kind.id(),Map.of("dni",true),Map.of("ciudad","Lima"));source.saveKind(new Kind(0,"No exportar","","",other.id(),"otro",true));
        new ApiCredentialStore(source.directory()).add("Privada","Factiliza","secret-test-token");data.saveContact(new Contact(0,"Persona","No exportar","","DNI","12345678","","","{}"));
        for(List<Long> selection:List.of(List.of(selected.id()),List.of(selected.id(),other.id()))){
            Path zip=temp.resolve("export-"+selection.size()+".zip");new StudioTransfer(source,data).exportConfiguration(zip,selection);Map<String,byte[]> entries=read(zip);assertThat(entries.keySet()).containsExactly("configuration.json");String payload=new String(entries.get("configuration.json"),java.nio.charset.StandardCharsets.UTF_8);assertThat(payload).doesNotContain("secret-test-token","12345678");
            JsonNode root=JSON.readTree(payload);assertThat(root.path("templates")).hasSize(selection.size());
            if(selection.size()==1){assertThat(root.path("definitions")).hasSize(3);assertThat(root.path("apiBindings")).hasSize(2);assertThat(root.path("groups")).hasSize(1);assertThat(root.path("groups").get(0).path("count").asInt()).isEqualTo(1);assertThat(root.path("kinds")).hasSize(1);assertThat(payload).doesNotContain("No exportar","Exclusivo");}
            DocumentStore target=new DocumentStore(temp.resolve("target-"+selection.size()));StudioData imported=new StudioData(target.directory());new StudioTransfer(target,imported).importConfiguration(zip,StudioTransfer.Conflict.KEEP);
            Template restored=target.templates().stream().filter(t->t.name().equals(selected.name())).findFirst().orElseThrow();assertThat(restored.original()).containsExactly(selected.original());assertThat(restored.edited()).containsExactly(selected.edited());assertThat(target.variables(restored.id())).extracting(Variable::key).containsExactlyInAnyOrder("dni","nombre","ciudad");assertThat(imported.apiContexts(restored.id())).hasSize(2);assertThat(imported.contactBindings(restored.id())).hasSize(1);assertThat(imported.mappings(restored.id())).hasSize(1);
            Kind restoredKind=target.kinds().stream().filter(k->k.title().equals("Carta")).findFirst().orElseThrow();assertThat(target.kindDefaults(restoredKind.id())).containsEntry("ciudad","Lima");
            try(XWPFDocument generated=DocumentEngine.open(DocumentEngine.generate(restored.edited(),Map.of("dni","12345678","nombre","Ana","ciudad","Lima")))){assertThat(generated.getParagraphs().get(0).getText()).isEqualTo("12345678 Ana Lima");}
        }
    }

    @Test void clientsAndCombinedPackagesDetectSectionsAndUpsertOnlyExactIdentity()throws Exception{
        DocumentStore source=new DocumentStore(temp.resolve("source"));StudioData data=new StudioData(source.directory());template(source,"Carta","Contenido");
        data.saveContact(new Contact(0,"Persona","Ana","Pérez","DNI","12345678","Lima","","{\"telefono\":\"555\"}"));data.saveContact(new Contact(0,"Empresa","ACME","","RUC","20123456789","Cusco","Representante","{\"capital\":100}"));
        Path config=temp.resolve("config.zip"),clients=temp.resolve("clients.zip");StudioTransfer transfer=new StudioTransfer(source,data);transfer.exportConfiguration(config);transfer.exportClients(clients);JsonNode clientRoot=JSON.readTree(read(clients).get("clients.json"));assertThat(clientRoot.path("format").asText()).isEqualTo("document-studio-clients");assertThat(clientRoot.path("version").asInt()).isEqualTo(1);
        DocumentStore target=new DocumentStore(temp.resolve("target"));StudioData imported=new StudioData(target.directory());StudioTransfer importer=new StudioTransfer(target,imported);
        imported.saveContact(new Contact(0,"Persona","Otro tipo","","CE","12345678","","","{}"));
        assertThat(importer.importConfiguration(clients,StudioTransfer.Conflict.KEEP).clients()).isEqualTo(2);assertThat(imported.contacts()).hasSize(3);assertThat(importer.importConfiguration(clients,StudioTransfer.Conflict.KEEP).clients()).isZero();assertThat(imported.contacts()).hasSize(3);
        Contact original=imported.matchingContact("Persona","DNI","12345678");imported.saveContact(new Contact(original.id(),original.kind(),"Local",original.family(),original.documentType(),original.number(),"Arequipa","","{\"local\":true}"));
        importer.importConfiguration(clients,StudioTransfer.Conflict.KEEP);assertThat(imported.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Local");
        Map<String,byte[]> combined=new LinkedHashMap<>(read(config));combined.putAll(read(clients));Path zip=temp.resolve("combined.zip");write(zip,combined);StudioTransfer.ImportResult result=importer.importConfiguration(zip,StudioTransfer.Conflict.REPLACE);assertThat(result.templates()).isEqualTo(1);assertThat(result.clients()).isEqualTo(2);assertThat(imported.contacts()).hasSize(3);assertThat(imported.matchingContact("Persona","DNI","12345678").extrasJson()).contains("555");assertThat(imported.matchingContact("Persona","CE","12345678").given()).isEqualTo("Otro tipo");assertThat(imported.matchingContact("Empresa","RUC","20123456789").representatives()).isEqualTo("Representante");
    }

    @Test void invalidClientVersionInCombinedPackageIsRejectedBeforeWritingTemplates()throws Exception{
        DocumentStore source=new DocumentStore(temp.resolve("source"));StudioData data=new StudioData(source.directory());template(source,"Carta","Texto");Path config=temp.resolve("config.zip");new StudioTransfer(source,data).exportConfiguration(config);Map<String,byte[]> entries=new LinkedHashMap<>(read(config));entries.put("clients.json","{\"format\":\"document-studio-clients\",\"version\":2,\"clients\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));Path invalid=temp.resolve("invalid.zip");write(invalid,entries);
        DocumentStore target=new DocumentStore(temp.resolve("target"));StudioData imported=new StudioData(target.directory());assertThatThrownBy(()->new StudioTransfer(target,imported).importConfiguration(invalid,StudioTransfer.Conflict.REPLACE)).hasMessageContaining("clients.json");assertThat(target.templates()).isEmpty();assertThat(imported.contacts()).isEmpty();
    }

    private static Template template(DocumentStore store,String name,String content)throws Exception{try(XWPFDocument document=new XWPFDocument()){document.createParagraph().createRun().setText(content);byte[] bytes=DocumentEngine.bytes(document);return store.saveTemplate(new Template(0,name,bytes,bytes));}}
    private static Map<String,byte[]> read(Path source)throws Exception{Map<String,byte[]> out=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(source))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)out.put(entry.getName(),zip.readAllBytes());}return out;}
    private static void write(Path destination,Map<String,byte[]> entries)throws Exception{try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(destination))){for(var e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}}
}
