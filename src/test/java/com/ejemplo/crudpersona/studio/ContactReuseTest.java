package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.Variable;
import com.ejemplo.crudpersona.studio.StudioData.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ContactReuseTest {
    @TempDir Path temp;

    @Test void partialApiUpdatesPreserveNonEmptyFieldsAllRawValuesAndExactIdentities()throws Exception{
        new DocumentStore(temp);StudioData data=new StudioData(temp);
        data.saveContact(new Contact(0,"Persona","CE","","CE","12345678","","","{}"));
        Contact first=data.upsertContact(ContactReuse.fromApi(result("DNI","12345678",Map.of("nombres","Ana","apellido_paterno","Pérez","direccion","Lima"),Map.of("data.ubigeo[2]","150101","data.extra","conservar"))));
        Contact updated=data.upsertContact(ContactReuse.fromApi(result("DNI","12345678",Map.of("nombres","Ana nueva","direccion",""),Map.of("data.sexo","F"))));
        assertThat(updated.id()).isEqualTo(first.id());assertThat(updated.given()).isEqualTo("Ana nueva");assertThat(updated.address()).isEqualTo("Lima");assertThat(updated.family()).isEqualTo("Pérez");assertThat(updated.extrasJson()).contains("data.extra","conservar","data.sexo");assertThat(ContactReuse.apiValues(updated)).containsEntry("ubigeo","150101").containsEntry("nombres","Ana nueva");assertThat(data.contacts()).hasSize(2);assertThat(data.matchingContact("Empresa","RUC","12345678")).isNull();
        Contact company=data.upsertContact(ContactReuse.fromApi(result("RUC","20123456789",Map.of("razon_social","ACME","direccion_completa","Cusco"),Map.of("data.representantes[0].nombre","Lucía"))));assertThat(company.representatives()).contains("Lucía");assertThat(company.extrasJson()).contains("data.representantes[0].nombre");
    }

    @Test void typedManualFormsAndApiContextsSaveOfflineWithoutConflictCallback()throws Exception{
        new DocumentStore(temp);StudioData data=new StudioData(temp);long template=1;
        List<Variable> vars=List.of(v("dni","DNI","DNI"),v("nombre","Nombres","Texto corto"),v("direccion","Domicilio","Domicilio"));
        List<ContactBinding> bindings=ContactReuse.formBindings(template,vars,List.of(),List.of(),Map.of());Map<String,String> values=new LinkedHashMap<>(Map.of("dni","12345678","nombre","Ana","direccion","Lima"));
        assertThat(ContactReuse.save(data,bindings,values,Map.of("dni","DNI")).created()).isEqualTo(1);values.put("nombre","Ana nueva");values.put("direccion","");
        assertThat(ContactReuse.save(data,bindings,values,Map.of("dni","DNI")).updated()).isEqualTo(1);assertThat(data.matchingContact("Persona","DNI","12345678").address()).isEqualTo("Lima");
        List<Variable> companies=List.of(v("numero","Identificador","Texto corto"),v("razon_social","Razón social","Texto corto"),v("direccion","Domicilio","Domicilio"));List<ApiContext> contexts=List.of(new ApiContext(1,"numero","empresa","RUC",true),new ApiContext(1,"razon_social","empresa","RUC",false),new ApiContext(1,"direccion","empresa","RUC",false));
        assertThat(ContactReuse.save(data,ContactReuse.formBindings(template,companies,List.of(),contexts,Map.of()),Map.of("numero","20123456789","razon_social","ACME","direccion","Cusco"),Map.of()).created()).isEqualTo(1);assertThat(data.matchingContact("Empresa","RUC","20123456789").given()).isEqualTo("ACME");
    }

    @Test void independentPeopleKeepTheirApiBoundFieldsSeparatedWhenEnteredManually()throws Exception{
        new DocumentStore(temp);StudioData data=new StudioData(temp);List<Variable> variables=List.of(v("documento_a","Documento","Texto corto"),v("dato_a","Dato","Texto corto"),v("documento_b","Documento","Texto corto"),v("dato_b","Dato","Texto corto"));
        List<ApiContext> contexts=List.of(new ApiContext(1,"documento_a","a","DNI",true),new ApiContext(1,"dato_a","a","DNI",false),new ApiContext(1,"documento_b","b","DNI",true),new ApiContext(1,"dato_b","b","DNI",false));
        Map<String,ApiBinding> api=Map.of("dato_a",new ApiBinding(1,"DNI","field:nombres"),"dato_b",new ApiBinding(2,"DNI","field:nombres"));List<ContactBinding> bindings=ContactReuse.formBindings(1,variables,List.of(),contexts,api);
        assertThat(ContactReuse.save(data,bindings,Map.of("documento_a","12345678","dato_a","Ana","documento_b","87654321","dato_b","María"),Map.of("documento_a","DNI","documento_b","DNI")).created()).isEqualTo(2);assertThat(data.matchingContact("Persona","DNI","12345678").given()).isEqualTo("Ana");assertThat(data.matchingContact("Persona","DNI","87654321").given()).isEqualTo("María");
    }
    @Test void fullNamesAndPartialSurnameUpdatesRetainTheirOriginalMeaning()throws Exception{
        new DocumentStore(temp);StudioData data=new StudioData(temp);
        Contact contact=data.upsertContact(ContactReuse.fromApi(result("DNI","12345678",Map.of("nombres","ANA","apellido_paterno","PÉREZ","apellido_materno","RUIZ","nombre_completo","PÉREZ RUIZ, ANA"),Map.of())));
        assertThat(ContactReuse.apiValues(contact)).containsEntry("nombre_completo","PÉREZ RUIZ, ANA");
        List<ContactBinding> bindings=ContactReuse.formBindings(1,List.of(v("dni","DNI","DNI"),v("completo","Dato","Texto corto")),List.of(),List.of(),Map.of("completo",new ApiBinding(1,"DNI","field:nombre_completo")));
        assertThat(ContactReuse.valuesFor(contact,bindings)).containsEntry("completo","PÉREZ RUIZ, ANA");
        Contact updated=data.upsertContact(ContactReuse.fromApi(result("DNI","12345678",Map.of("apellido_paterno","GÓMEZ"),Map.of())));
        assertThat(updated.family()).isEqualTo("GÓMEZ RUIZ");assertThat(updated.given()).isEqualTo("ANA");
    }

    private static Variable v(String key,String label,String type){return new Variable(0,1,key,label,type,"",false,1);}
    private static LookupRouter.Result result(String service,String number,Map<String,String> values,Map<String,String> raw){return new LookupRouter.Result(service,number,"Factiliza",values,values.keySet(),Set.of(),LookupRouter.Status.PARTIAL,"",raw);}
}
