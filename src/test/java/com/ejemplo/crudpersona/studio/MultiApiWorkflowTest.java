package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class MultiApiWorkflowTest {
    @TempDir Path temp;
    private static Clock clock(String date){return Clock.fixed(LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant(),ZoneId.systemDefault());}

    @Test void normalizesDocumentedExamplesWithoutInventingAbsentFields()throws Exception{
        String factiliza="{\"status\":200,\"success\":true,\"data\":{\"numero\":\"12345678\",\"nombres\":\"ANA\",\"apellido_paterno\":\"PEREZ\",\"nombre_completo\":\"PEREZ, ANA\",\"ubigeo\":[\"15\",\"1501\",\"150101\"],\"fecha_nacimiento\":null}}";
        Map<String,String> raw=FactilizaClient.parse("DNI","12345678",200,factiliza).data();
        assertThat(ApiFieldCatalog.normalize("Factiliza","DNI",raw)).containsEntry("nombre_completo","PEREZ, ANA").containsEntry("ubigeo","150101").doesNotContainKey("fecha_nacimiento");
        String peru="{\"dni\":\"12345678\",\"nombres\":\"ANA\",\"apellidoPaterno\":\"PEREZ\",\"apellidoMaterno\":\"RUIZ\",\"codVerifica\":\"1\"}";
        assertThat(ApiFieldCatalog.normalize("APIsPERU","DNI",ApisPeruProvider.parse("DNI",200,peru,0))).containsEntry("apellido_paterno","PEREZ").doesNotContainKey("direccion").doesNotContainKey("nombre_completo");
        assertThat(ApiFieldCatalog.canonical("DNI","data.apellido_paterno")).isEqualTo("apellido_paterno");
        assertThat(ApiFieldCatalog.canonical("DNI","data.campo_desconocido")).startsWith("legacy:");
    }

    @Test void fallsBackAfterTimeoutTracksMonthlyUsageAndKeepsInvalidCredentials()throws Exception{
        Path dir=temp.resolve("router");new DocumentStore(dir);ApiCredentialStore store=new ApiCredentialStore(dir,clock("2026-11-12"));
        ApiCredentialStore.Credential first=store.add("Principal","Factiliza","private-factiliza-token");
        ApiCredentialStore.Credential second=store.add("Respaldo","APIsPERU","private-apisperu-token");
        AtomicInteger firstCalls=new AtomicInteger(),secondCalls=new AtomicInteger();
        LookupProvider factiliza=new LookupProvider(){public String name(){return "Factiliza";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String service,String number,String token)throws Exception{firstCalls.incrementAndGet();throw new HttpTimeoutException("timeout");}};
        LookupProvider peru=new LookupProvider(){public String name(){return "APIsPERU";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String service,String number,String token){secondCalls.incrementAndGet();return Map.of("dni",number,"nombres","ANA");}};
        LookupRouter router=new LookupRouter(store,List.of(factiliza,peru));LookupRouter.Result result=router.query("DNI","12345678");
        assertThat(result.provider()).isEqualTo("APIsPERU");assertThat(result.values()).containsEntry("nombres","ANA");assertThat(firstCalls).hasValue(1);assertThat(secondCalls).hasValue(1);
        assertThat(store.credentials().stream().filter(c->c.id()==first.id()).findFirst().orElseThrow().requests()).isEqualTo(1);
        assertThat(store.credentials().stream().filter(c->c.id()==second.id()).findFirst().orElseThrow().successes()).isEqualTo(1);
        store.record(first.id(),LookupRouter.Status.INVALID,0);assertThat(store.credentials()).hasSize(2);
        ApiCredentialStore nextMonth=new ApiCredentialStore(dir,clock("2026-12-01"));assertThat(nextMonth.credentials().stream().filter(c->c.id()==first.id()).findFirst().orElseThrow().requests()).isZero();
        nextMonth.record(first.id(),LookupRouter.Status.INVALID,0);assertThat(nextMonth.credentials().stream().filter(c->c.id()==first.id()).findFirst().orElseThrow().invalidStreak()).isEqualTo(2);
        assertThat(nextMonth.credentials()).hasSize(2);
        nextMonth.record(first.id(),LookupRouter.Status.SUCCESS,0);assertThat(nextMonth.credentials().stream().filter(c->c.id()==first.id()).findFirst().orElseThrow().invalidStreak()).isZero();
        assertThat(java.nio.file.Files.readString(dir.resolve("studio.db"),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("private-factiliza-token","private-apisperu-token");
    }

    @Test void rateLimitSkipsOtherKeyOfSameProviderAndOfflineStaysManual()throws Exception{
        Path dir=temp.resolve("rate");new DocumentStore(dir);ApiCredentialStore store=new ApiCredentialStore(dir);
        store.add("F1","Factiliza","token-one");store.add("F2","Factiliza","token-two");store.add("P1","APIsPERU","token-three");
        AtomicInteger factCalls=new AtomicInteger();
        LookupProvider limited=new LookupProvider(){public String name(){return "Factiliza";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String s,String n,String t)throws Exception{factCalls.incrementAndGet();throw new FactilizaClient.HttpStatusException(429,120,"limit");}};
        LookupProvider alternative=new LookupProvider(){public String name(){return "APIsPERU";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String s,String n,String t){return Map.of("ruc",n,"razonSocial","EMPRESA");}};
        LookupRouter router=new LookupRouter(store,List.of(limited,alternative));assertThat(router.query("RUC","20123456789").provider()).isEqualTo("APIsPERU");assertThat(factCalls).hasValue(1);
        for(ApiCredentialStore.Credential c:store.credentials())store.enabled(c.id(),false);
        LookupRouter.Result offline=router.query("RUC","20123456789");assertThat(offline.status()).isEqualTo(LookupRouter.Status.NO_CREDENTIAL);assertThat(offline.values()).isEmpty();
    }

    @Test void documentedFactilizaResponseAndPartialApisPeruStayEditable()throws Exception{
        Path dir=temp.resolve("responses");new DocumentStore(dir);ApiCredentialStore vault=new ApiCredentialStore(dir);
        ApiCredentialStore.Credential first=vault.add("F","Factiliza","test-only");vault.add("P","APIsPERU","test-only");
        LookupProvider factiliza=new LookupProvider(){public String name(){return "Factiliza";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String s,String n,String t){return Map.of("data.numero",n,"data.nombres","ANA","data.apellido_paterno","PEREZ");}};
        LookupProvider peru=new LookupProvider(){public String name(){return "APIsPERU";}public Set<String> services(){return Set.of("DNI","RUC");}public Map<String,String> query(String s,String n,String t){return Map.of("dni",n,"nombres","ANA");}};
        LookupRouter.Result result=new LookupRouter(vault,List.of(factiliza,peru)).query("DNI","12345678");
        assertThat(result.provider()).isEqualTo("Factiliza");assertThat(result.values()).containsEntry("apellido_paterno","PEREZ");assertThat(result.unavailable()).contains("sexo");
        vault.enabled(first.id(),false);LookupRouter.Result partial=new LookupRouter(vault,List.of(factiliza,peru)).query("DNI","12345678");
        assertThat(partial.status()).isEqualTo(LookupRouter.Status.PARTIAL);assertThat(partial.values()).doesNotContainKey("apellido_paterno");assertThat(partial.unavailable()).contains("apellido_paterno");
    }

    @Test void multipleGroupsPersistTransferAndDoNotDuplicateTemplate()throws Exception{
        Path source=temp.resolve("groups-source");DocumentStore store=new DocumentStore(source);StudioData data=new StudioData(source);
        byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{nombre}} {{nombre}}");bytes=DocumentEngine.bytes(doc);}
        Template template=store.saveTemplate(new Template(0,"Carta",bytes,bytes));TemplateGroup a=store.saveGroup(0,"Solicitudes"),b=store.saveGroup(0,"Frecuentes");
        store.setTemplateGroups(template.id(),List.of(a.id(),b.id()));assertThat(store.groupIds(template.id())).containsExactlyInAnyOrder(a.id(),b.id());
        assertThat(store.templates()).hasSize(1);store.setTemplateGroups(template.id(),List.of(b.id()));assertThat(store.groupIds(template.id())).containsExactly(b.id());
        store.setTemplateGroups(template.id(),List.of(a.id(),b.id()));
        Path zip=temp.resolve("groups.zip");new StudioTransfer(store,data).exportConfiguration(zip);
        Path destination=temp.resolve("groups-destination");DocumentStore imported=new DocumentStore(destination);StudioData importedData=new StudioData(destination);new StudioTransfer(imported,importedData).importConfiguration(zip,StudioTransfer.Conflict.KEEP);
        assertThat(imported.templates()).hasSize(1);assertThat(imported.groupIds(imported.templates().get(0).id())).hasSize(2);assertThat(new DocumentStore(destination).groupIds(imported.templates().get(0).id())).hasSize(2);
        imported.deleteGroup(imported.groups().get(0).id(),null);assertThat(imported.templates()).hasSize(1);
        assertThat(DocumentEngine.generate(bytes,Map.of("nombre","Ana"))).isNotEmpty();
    }

    @Test void encryptedBackupAndOrdinaryTransferExcludeCredentialTokens()throws Exception{
        Path source=temp.resolve("backup-source"),destination=temp.resolve("backup-destination");DocumentStore store=new DocumentStore(source);StudioData data=new StudioData(source);ApiCredentialStore vault=new ApiCredentialStore(source);
        ApiCredentialStore.Credential credential=vault.add("Cuenta privada","APIsPERU","token-super-privado");
        Path config=temp.resolve("ordinary.zip");new StudioTransfer(store,data).exportConfiguration(config);
        assertThat(java.nio.file.Files.readString(config,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("token-super-privado");
        Path backup=temp.resolve("full.dsbk");new StudioTransfer(store,data).backup(backup,"password-safe".toCharArray());
        assertThat(java.nio.file.Files.readString(backup,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("token-super-privado");
        new DocumentStore(destination);StudioData destinationData=new StudioData(destination);new StudioTransfer(new DocumentStore(destination),destinationData).restore(backup,"password-safe".toCharArray());
        ApiCredentialStore restored=new ApiCredentialStore(destination);assertThat(restored.token(credential.id())).isNull();assertThat(vault.token(credential.id())).isEqualTo("token-super-privado");
    }

    @Test void legacyGroupAssociationMigratesWithoutChangingTemplate()throws Exception{
        Path dir=temp.resolve("legacy-group");DocumentStore initial=new DocumentStore(dir);
        byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("Plantilla");bytes=DocumentEngine.bytes(doc);}
        Template template=initial.saveTemplate(new Template(0,"Anterior",bytes,bytes));TemplateGroup group=initial.saveGroup(0,"Anterior");
        try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("studio.db"));var s=c.prepareStatement("INSERT INTO template_group_member(template_id,group_id) VALUES(?,?)")){s.setLong(1,template.id());s.setLong(2,group.id());s.executeUpdate();}
        DocumentStore migrated=new DocumentStore(dir);assertThat(migrated.groupIds(template.id())).containsExactly(group.id());assertThat(migrated.templates()).hasSize(1);
        assertThat(new DocumentStore(dir).groupIds(template.id())).containsExactly(group.id());
    }

    @Test void independentApiContextsSurviveTransferAndNeverCrossFill()throws Exception{
        Path source=temp.resolve("contexts-source");DocumentStore store=new DocumentStore(source);StudioData data=new StudioData(source);
        byte[] bytes;try(XWPFDocument doc=new XWPFDocument()){doc.createParagraph().createRun().setText("{{dni_1}} {{nombre_1}} {{dni_2}} {{nombre_2}}");bytes=DocumentEngine.bytes(doc);}
        Template template=store.saveTemplate(new Template(0,"Dos personas",bytes,bytes));
        for(String key:List.of("dni_1","nombre_1","dni_2","nombre_2"))store.saveVariable(new Variable(0,template.id(),key,key,"Texto corto","",false,1));
        for(int i=1;i<=2;i++){data.saveApiContext(new StudioData.ApiContext(template.id(),"dni_"+i,"persona_"+i,"DNI",true));data.saveApiContext(new StudioData.ApiContext(template.id(),"nombre_"+i,"persona_"+i,"DNI",false));}
        assertThat(StudioWindow.sameQueryGroup("persona_1","persona_2",2,"dni_1","nombre_2")).isFalse();
        assertThat(StudioWindow.sameQueryGroup("persona_1","persona_1",2,"dni_1","nombre_1")).isTrue();
        Path zip=temp.resolve("contexts.zip");new StudioTransfer(store,data).exportConfiguration(zip);
        Path target=temp.resolve("contexts-target");DocumentStore imported=new DocumentStore(target);StudioData importedData=new StudioData(target);
        new StudioTransfer(imported,importedData).importConfiguration(zip,StudioTransfer.Conflict.KEEP);
        assertThat(importedData.apiContexts(imported.templates().get(0).id())).hasSize(4);
        assertThat(new StudioData(target).apiContexts(imported.templates().get(0).id())).hasSize(4);
    }
}
