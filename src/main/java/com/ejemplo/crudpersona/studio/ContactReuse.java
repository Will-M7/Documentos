package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.DocumentStore.Variable;
import com.ejemplo.crudpersona.studio.StudioData.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.text.Normalizer;
import java.util.*;

/** Shared local-first contact values and automatic non-empty merging. */
public final class ContactReuse {
    public record Result(int created,int updated,int unchanged,int skipped){}
    private static final ObjectMapper JSON=new ObjectMapper();
    private ContactReuse(){}

    public static Map<String,String> valuesFor(Contact contact,List<ContactBinding> group){
        Map<String,String> values=new LinkedHashMap<>();
        Map<String,String> extras=apiValues(contact);
        for(ContactBinding b:group){
            String value=switch(b.field()){
                case "given"->contact.given();case "family"->contact.family();case "document_type"->contact.documentType();
                case "number"->contact.number();case "address"->contact.address();case "representatives"->contact.representatives();
                default->extras.getOrDefault(b.field().startsWith("extra:")?b.field().substring(6):b.field(),"");
            };
            values.put(b.variableKey(),value==null?"":value);
        }
        return values;
    }

    /** Both canonical values and every provider path survive partial responses and offline reuse. */
    public static Contact fromApi(LookupRouter.Result result)throws Exception{
        Map<String,String> fields=new LinkedHashMap<>(result.raw());fields.putAll(result.values());
        boolean company="RUC".equals(result.service());
        String name=company?fields.getOrDefault("razon_social",""):fields.getOrDefault("nombres","");
        if(name.isBlank())name=fields.getOrDefault(company?"nombre_o_razon_social":"nombre_completo","");
        String family=(fields.getOrDefault("apellido_paterno","")+" "+fields.getOrDefault("apellido_materno","")).trim();
        String address=prefer(fields.get("direccion_completa"),fields.getOrDefault("direccion",""));
        String reps=fields.entrySet().stream().filter(e->e.getKey().toLowerCase(Locale.ROOT).contains("representante"))
                .map(Map.Entry::getValue).filter(v->v!=null&&!v.isBlank()).distinct().collect(java.util.stream.Collectors.joining("; "));
        return new Contact(0,company?"Empresa":"Persona",name,company?"":family,result.service(),result.identifier().trim(),address,reps,JSON.writeValueAsString(fields));
    }

    public static Map<String,String> apiValues(Contact contact){
        Map<String,String> values=new LinkedHashMap<>();
        try{JsonNode extras=JSON.readTree(contact.extrasJson());if(extras!=null)flatten(extras,"",values);}catch(Exception ignored){/* Legacy base fields remain usable. */}
        String service="Empresa".equals(contact.kind())?"RUC":"DNI";
        for(String provider:List.of("Factiliza","APIsPERU"))ApiFieldCatalog.normalize(provider,service,values).forEach(values::putIfAbsent);
        values.put("numero",contact.number());
        if("RUC".equals(service))putNonEmpty(values,"razon_social",contact.given());
        else{
            putNonEmpty(values,"nombres",contact.given());
            putNonEmpty(values,"apellidos",contact.family());
            values.putIfAbsent("nombre_completo",(contact.given()+" "+contact.family()).trim());
        }
        putNonEmpty(values,"direccion",contact.address());putNonEmpty(values,"direccion_completa",contact.address());
        putNonEmpty(values,"representantes",contact.representatives());
        return values;
    }
    private static void flatten(JsonNode node,String path,Map<String,String> values){
        if(node.isObject())node.fields().forEachRemaining(e->flatten(e.getValue(),path.isEmpty()?e.getKey():path+"."+e.getKey(),values));
        else if(node.isArray()){for(int i=0;i<node.size();i++)flatten(node.get(i),path+"["+i+"]",values);}
        else if(!node.isNull())values.put(path,node.asText());
    }
    private static void putNonEmpty(Map<String,String> values,String key,String value){if(value!=null&&!value.isBlank())values.put(key,value);}

    public static Result save(StudioData data,List<ContactBinding> bindings,Map<String,String> values,
                              Map<String,String> variableTypes)throws SQLException{
        Map<String,List<ContactBinding>> groups=new LinkedHashMap<>();
        for(ContactBinding binding:bindings)groups.computeIfAbsent(binding.groupKey(),ignored->new ArrayList<>()).add(binding);
        int created=0,updated=0,unchanged=0,skipped=0;
        for(List<ContactBinding> group:groups.values()){
            String kind=group.get(0).kind();Map<String,String> fields=new LinkedHashMap<>();
            for(ContactBinding b:group){String value=values.getOrDefault(b.variableKey(),"").trim();if(!value.isBlank())fields.put(b.field(),value);}
            String name=fields.getOrDefault("given",fields.getOrDefault("nombre_completo","")),number=fields.getOrDefault("number","");
            String docType=fields.getOrDefault("document_type","");
            if("Empresa".equals(kind))docType="RUC";
            else if(docType.isBlank()&&number.matches("\\d{8}"))docType="DNI";
            if(!validIdentifier(kind,docType,number)){skipped++;continue;}
            try{
                Map<String,String> extras=new LinkedHashMap<>(values);
                for(ContactBinding b:group){String value=values.getOrDefault(b.variableKey(),"").trim();if(!value.isBlank()){
                    extras.put(b.variableKey(),value);
                    String canonical=switch(b.field()){case "number"->"numero";case "given"->"Empresa".equals(kind)?"razon_social":"nombres";case "family"->"apellidos";case "address"->"direccion";case "representatives"->"representantes";default->b.field().startsWith("extra:")?b.variableKey():b.field();};
                    extras.put(canonical,value);
                }}
                String family=fields.getOrDefault("family",(fields.getOrDefault("apellido_paterno","")+" "+fields.getOrDefault("apellido_materno","")).trim());
                Contact incoming=new Contact(0,kind,name,family,docType,number,fields.getOrDefault("address",""),fields.getOrDefault("representatives",""),JSON.writeValueAsString(extras));
                Contact existing=data.matchingContact(kind,docType,number);
                if(existing==null){data.upsertContact(incoming);created++;continue;}
                Contact merged=StudioData.mergeContact(existing,incoming);
                if(existing.equals(merged)){unchanged++;continue;}
                data.upsertContact(incoming);updated++;
            }catch(java.io.IOException e){throw new SQLException("No se pudo guardar el cliente.",e);}
        }
        return new Result(created,updated,unchanged,skipped);
    }

    /** Explicit associations win; implicit fields are used only for an unambiguous query group. */
    public static List<ContactBinding> formBindings(long templateId,List<Variable> variables,List<ContactBinding> explicit,
                                                     List<ApiContext> contexts,Map<String,ApiBinding> api){
        List<ContactBinding> result=new ArrayList<>(explicit);
        Map<String,String> sources=new LinkedHashMap<>(),services=new LinkedHashMap<>();
        for(Variable v:variables){
            ApiContext context=contexts.stream().filter(c->c.variableKey().equals(v.key())).findFirst().orElse(null);
            ContactBinding bound=explicit.stream().filter(b->b.variableKey().equals(v.key())).findFirst().orElse(null);
            ApiBinding binding=api.get(v.key());
            String service;
            if(context!=null)service=context.source()?context.service():null;
            else if(bound!=null)service="number".equals(bound.field())?("Empresa".equals(bound.kind())?"RUC":"DNI"):null;
            else if(List.of("DNI","RUC").contains(v.type()))service=v.type();
            else if(binding!=null)service="numero".equals(ApiFieldCatalog.canonical(binding.service(),binding.path()))?binding.service():null;
            else service=identifierService(v);
            if(service==null)continue;
            String group=context!=null?context.groupKey():bound!=null?bound.groupKey():"document:"+v.key();
            sources.put(v.key(),group);services.put(v.key(),service);
            if(bound==null)result.add(new ContactBinding(0,templateId,group,"RUC".equals(service)?"Empresa":"Persona","number",v.key()));
        }
        for(Variable v:variables){
            if(result.stream().anyMatch(b->b.variableKey().equals(v.key())))continue;
            ApiContext context=contexts.stream().filter(c->c.variableKey().equals(v.key())).findFirst().orElse(null);
            ApiBinding binding=api.get(v.key());
            Set<String> candidates=new LinkedHashSet<>();
            for(var source:sources.entrySet())if((context==null||context.groupKey().equals(source.getValue())&&context.service().equals(services.get(source.getKey())))&&(binding==null||binding.service().equals(services.get(source.getKey()))))candidates.add(source.getValue());
            if(candidates.size()!=1)continue;
            String group=candidates.iterator().next();String source=sources.keySet().stream().filter(k->sources.get(k).equals(group)).findFirst().orElseThrow();String service=services.get(source);
            String canonical=binding==null?null:ApiFieldCatalog.canonical(service,binding.path());
            String field=canonical==null?manualField(v):switch(canonical){
                case "nombres","razon_social"->"given";case "apellidos"->"family";case "direccion","direccion_completa"->"address";case "representantes"->"representatives";case "numero"->"number";default->canonical;
            };
            boolean duplicate=false;for(ContactBinding b:result)if(b.groupKey().equals(group)&&b.field().equals(field))duplicate=true;
            if(field==null||duplicate)field="extra:"+v.key();
            result.add(new ContactBinding(0,templateId,group,"RUC".equals(service)?"Empresa":"Persona",field,v.key()));
        }
        return result;
    }
    static String identifierService(Variable variable){
        if(List.of("DNI","RUC").contains(variable.type()))return variable.type();
        String text=normalizedName(variable);
        if(text.contains("ruc"))return "RUC";
        return text.contains("dni")?"DNI":null;
    }
    private static String normalizedName(Variable variable){
        return Normalizer.normalize(variable.key()+" "+variable.label(),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("([a-z])([A-Z])","$1 $2").toLowerCase(Locale.ROOT).replace('_',' ');
    }
    private static String manualField(Variable v){
        String text=normalizedName(v);
        if("Domicilio".equals(v.type())||text.matches(".*\\b(direccion|domicilio)\\b.*"))return "address";
        if(text.matches(".*\\b(representantes?)\\b.*"))return "representatives";
        if(text.matches(".*\\b(apellido paterno|primer apellido)\\b.*"))return "apellido_paterno";
        if(text.matches(".*\\b(apellido materno|segundo apellido)\\b.*"))return "apellido_materno";
        if(text.matches(".*\\b(apellidos?)\\b.*"))return "family";
        if(text.matches(".*\\b(nombres?|razon social|cliente|empresa)\\b.*"))return "given";
        return null;
    }
    private static boolean validIdentifier(String kind,String type,String number){
        if("Empresa".equals(kind))return number.matches("\\d{11}");
        if(type.isBlank())return false;
        if("DNI".equalsIgnoreCase(type))return number.matches("\\d{8}");
        return number.matches("[A-Za-z0-9-]{4,30}");
    }
    private static String prefer(String newer,String older){return newer==null||newer.isBlank()?older:newer;}
}
