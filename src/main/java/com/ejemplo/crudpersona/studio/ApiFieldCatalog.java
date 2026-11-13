package com.ejemplo.crudpersona.studio;

import java.util.*;

/** Provider-neutral fields shown to the user; paths stay behind the adapters. */
public final class ApiFieldCatalog {
    public record Field(String service,String key,String label,String factilizaPath,String apisPeruPath){
        @Override public String toString(){return label;}
    }
    private static final List<Field> FIELDS=List.of(
        new Field("DNI","numero","Número de DNI","data.numero","dni"),
        new Field("DNI","nombres","Nombres","data.nombres","nombres"),
        new Field("DNI","apellido_paterno","Apellido paterno","data.apellido_paterno","apellidoPaterno"),
        new Field("DNI","apellido_materno","Apellido materno","data.apellido_materno","apellidoMaterno"),
        new Field("DNI","nombre_completo","Nombre completo","data.nombre_completo",null),
        new Field("DNI","direccion","Dirección","data.direccion",null),
        new Field("DNI","direccion_completa","Dirección completa","data.direccion_completa",null),
        new Field("DNI","departamento","Departamento","data.departamento",null),
        new Field("DNI","provincia","Provincia","data.provincia",null),
        new Field("DNI","distrito","Distrito","data.distrito",null),
        new Field("DNI","ubigeo","Ubigeo","data.ubigeo[2]",null),
        new Field("DNI","ubigeo_reniec","Ubigeo RENIEC","data.ubigeo_reniec",null),
        new Field("DNI","ubigeo_sunat","Ubigeo SUNAT","data.ubigeo_sunat",null),
        new Field("DNI","fecha_nacimiento","Fecha de nacimiento","data.fecha_nacimiento",null),
        new Field("DNI","sexo","Sexo","data.sexo",null),
        new Field("DNI","codigo_verificacion","Código de verificación",null,"codVerifica"),
        new Field("RUC","numero","Número de RUC","data.numero","ruc"),
        new Field("RUC","razon_social","Razón social","data.nombre_o_razon_social","razonSocial"),
        new Field("RUC","nombre_comercial","Nombre comercial",null,"nombreComercial"),
        new Field("RUC","tipo_contribuyente","Tipo de contribuyente","data.tipo_contribuyente",null),
        new Field("RUC","estado","Estado del contribuyente","data.estado","estado"),
        new Field("RUC","condicion","Condición del contribuyente","data.condicion","condicion"),
        new Field("RUC","direccion","Dirección fiscal","data.direccion","direccion"),
        new Field("RUC","direccion_completa","Dirección completa","data.direccion_completa",null),
        new Field("RUC","departamento","Departamento","data.departamento","departamento"),
        new Field("RUC","provincia","Provincia","data.provincia","provincia"),
        new Field("RUC","distrito","Distrito","data.distrito","distrito"),
        new Field("RUC","ubigeo","Ubigeo","data.ubigeo[2]","ubigeo"),
        new Field("RUC","ubigeo_sunat","Ubigeo SUNAT","data.ubigeo_sunat",null),
        new Field("RUC","telefonos","Teléfonos",null,"telefonos[0]"),
        new Field("RUC","capital","Capital",null,"capital")
    );
    private ApiFieldCatalog(){}
    public static List<Field> fields(String service){return FIELDS.stream().filter(f->f.service().equals(service)).toList();}
    public static Field field(String service,String key){return fields(service).stream().filter(f->f.key().equals(key)).findFirst().orElse(null);}
    public static String canonical(String service,String stored){
        if(stored==null||stored.isBlank())return null;
        if(stored.startsWith("field:"))return stored.substring(6);
        for(Field f:fields(service))if(stored.equals(f.factilizaPath())||stored.equals(f.apisPeruPath())||stored.equals(f.factilizaPath()==null?null:f.factilizaPath().replace("data.","")))return f.key();
        return "legacy:"+stored;
    }
    public static String label(String service,String key){Field f=field(service,key);return f==null?"Campo anterior sin equivalencia":f.label();}
    public static Set<String> supportedKeys(String provider,String service){Set<String> out=new LinkedHashSet<>();for(Field f:fields(service))if(("Factiliza".equals(provider)?f.factilizaPath():f.apisPeruPath())!=null)out.add(f.key());return out;}
    public static Map<String,String> normalize(String provider,String service,Map<String,String> raw){Map<String,String> values=new LinkedHashMap<>();for(Field field:fields(service)){String path="Factiliza".equals(provider)?field.factilizaPath():field.apisPeruPath();if(path==null)continue;String value=raw.get(path);if("APIsPERU".equals(provider)&&"telefonos".equals(field.key())){List<String> numbers=new ArrayList<>();for(int i=0;raw.containsKey("telefonos["+i+"]");i++){String number=raw.get("telefonos["+i+"]");if(number!=null&&!number.isBlank())numbers.add(number);}value=String.join(", ",numbers);}if(value!=null&&!value.isBlank())values.put(field.key(),value);}return values;}
    public static String legacyValue(String provider,String stored,Map<String,String> raw){return "Factiliza".equals(provider)?raw.get(stored):null;}
}
