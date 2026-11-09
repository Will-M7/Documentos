package com.ejemplo.crudpersona.studio;

import com.ejemplo.crudpersona.studio.StudioData.Contact;
import com.ejemplo.crudpersona.studio.StudioData.ContactBinding;

import java.sql.SQLException;
import java.util.*;
import java.util.function.BiPredicate;

/** Explicitly mapped local contacts. No document roles or variable names are inferred. */
public final class ContactReuse {
    public record Result(int created,int updated,int unchanged,int skipped){}
    private ContactReuse(){}

    public static Map<String,String> valuesFor(Contact contact,List<ContactBinding> group){
        Map<String,String> values=new LinkedHashMap<>();
        for(ContactBinding b:group){
            String value=switch(b.field()){
                case "given"->contact.given();case "family"->contact.family();case "document_type"->contact.documentType();
                case "number"->contact.number();case "address"->contact.address();case "representatives"->contact.representatives();
                default->"";
            };
            values.put(b.variableKey(),value==null?"":value);
        }
        return values;
    }

    public static Result save(StudioData data,List<ContactBinding> bindings,Map<String,String> values,
                              Map<String,String> variableTypes,BiPredicate<Contact,Contact> confirmConflict)throws SQLException{
        Map<String,List<ContactBinding>> groups=new LinkedHashMap<>();
        for(ContactBinding binding:bindings)groups.computeIfAbsent(binding.groupKey(),ignored->new ArrayList<>()).add(binding);
        int created=0,updated=0,unchanged=0,skipped=0;
        for(List<ContactBinding> group:groups.values()){
            String kind=group.get(0).kind();Map<String,String> fields=new HashMap<>();
            for(ContactBinding b:group)fields.put(b.field(),values.getOrDefault(b.variableKey(),"").trim());
            String name=fields.getOrDefault("given",""),number=fields.getOrDefault("number","");
            String docType=fields.getOrDefault("document_type","");
            if("Empresa".equals(kind))docType="RUC";
            else if(docType.isBlank())for(ContactBinding b:group)if("number".equals(b.field())&&"DNI".equals(variableTypes.get(b.variableKey())))docType="DNI";
            if(name.isBlank()||!validIdentifier(kind,docType,number)){skipped++;continue;}
            Contact incoming=new Contact(0,kind,name,fields.getOrDefault("family",""),docType,number,fields.getOrDefault("address",""),fields.getOrDefault("representatives",""),"{}");
            Contact existing=data.matchingContact(kind,docType,number);
            if(existing==null){data.saveContact(incoming);created++;continue;}
            Contact merged=new Contact(existing.id(),kind,prefer(incoming.given(),existing.given()),prefer(incoming.family(),existing.family()),docType,number,
                    prefer(incoming.address(),existing.address()),prefer(incoming.representatives(),existing.representatives()),existing.extrasJson());
            if(sameData(existing,merged)){unchanged++;continue;}
            boolean conflict=contradicts(existing.given(),incoming.given())||contradicts(existing.family(),incoming.family())||contradicts(existing.address(),incoming.address())||contradicts(existing.representatives(),incoming.representatives());
            if(conflict&&!confirmConflict.test(existing,merged)){unchanged++;continue;}
            data.saveContact(merged);updated++;
        }
        return new Result(created,updated,unchanged,skipped);
    }
    private static boolean validIdentifier(String kind,String type,String number){
        if("Empresa".equals(kind))return number.matches("\\d{11}");
        if(type.isBlank())return false;
        if("DNI".equalsIgnoreCase(type))return number.matches("\\d{8}");
        return number.matches("[A-Za-z0-9-]{4,30}");
    }
    private static String prefer(String newer,String older){return newer==null||newer.isBlank()?older:newer;}
    private static boolean contradicts(String existing,String newer){return existing!=null&&!existing.isBlank()&&newer!=null&&!newer.isBlank()&&!existing.equals(newer);}
    private static boolean sameData(Contact a,Contact b){return Objects.equals(a.given(),b.given())&&Objects.equals(a.family(),b.family())&&Objects.equals(a.address(),b.address())&&Objects.equals(a.representatives(),b.representatives());}
}
