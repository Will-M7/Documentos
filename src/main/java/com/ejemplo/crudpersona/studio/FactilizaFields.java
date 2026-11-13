package com.ejemplo.crudpersona.studio;

import java.util.*;

/** Paths shown in the official sample responses. Actual responses can add more paths. */
public final class FactilizaFields {
    private FactilizaFields(){}
    public static List<String> paths(String service){
        List<String> common=List.of("numero","departamento","provincia","distrito","direccion","direccion_completa","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]");
        List<String> fields=new ArrayList<>();
        switch(service){
            case "DNI"->{fields.addAll(List.of("numero","nombres","apellido_paterno","apellido_materno","nombre_completo","departamento","provincia","distrito","direccion","direccion_completa","ubigeo_reniec","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]","fecha_nacimiento","sexo"));}
            case "RUC"->{fields.addAll(List.of("numero","nombre_o_razon_social","tipo_contribuyente","estado","condicion"));fields.addAll(common.subList(1,common.size()));}
            case "RUC - Representantes"->fields.addAll(List.of("tipo_de_documento","numero_de_documento","nombre","cargo","fecha_desde"));
            case "RUC - Establecimientos"->fields.addAll(List.of("codigo","tipo_establecimiento","actividad_economica","direccion","direccion_completa","departamento","provincia","distrito","ubigeo_sunat","ubigeo[0]","ubigeo[1]","ubigeo[2]"));
            case "CEE"->fields.addAll(List.of("numero","nombres","apellido_paterno","apellido_materno"));
            default->{}
        }
        String prefix=service.startsWith("RUC - ")?"data[0].":"data.";
        return fields.stream().map(f->prefix+f).toList();
    }
}
