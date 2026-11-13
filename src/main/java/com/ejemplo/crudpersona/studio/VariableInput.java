package com.ejemplo.crudpersona.studio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Input rules; legacy types remain readable without rewriting saved values. */
public final class VariableInput {
    public static final List<String> TYPES=List.of("Texto","Número","Mixto","Fecha","Número entero","Número decimal");
    private VariableInput(){}
    public static String visualType(String stored){return switch(stored){case "Texto corto","Texto largo","Domicilio"->"Texto";case "DNI","RUC"->"Mixto";case "Monto"->"Número decimal";default->stored;};}
    public static String initialValue(String type,String configured){return "Fecha".equals(type)?LocalDate.now().toString():configured==null?"":configured;}
    public static String validate(String type,String value,Locale locale){
        String v=value==null?"":value.trim();if(v.isEmpty())return "";
        switch(type){
            case "Fecha"->{try{return LocalDate.parse(v).toString();}catch(Exception ignored){throw new IllegalArgumentException("Fecha inválida. Use una fecha del calendario.");}}
            case "Número entero"->{if(!v.matches("[+-]?\\d+"))throw new IllegalArgumentException("Se requiere un número entero.");return v;}
            case "Número decimal","Monto"->{String normalized=normalizeDecimal(v,locale);try{new BigDecimal(normalized);}catch(Exception ex){throw new IllegalArgumentException("Número decimal inválido.");}return normalized;}
            case "Número"->{if(!v.matches("[+-]?[\\d.,\\s]+"))throw new IllegalArgumentException("Se requiere una entrada numérica.");return v;}
            case "DNI"->{if(!v.matches("\\d{8}"))throw new IllegalArgumentException("DNI debe tener ocho dígitos.");return v;}
            case "RUC"->{if(!v.matches("\\d{11}"))throw new IllegalArgumentException("RUC debe tener once dígitos.");return v;}
            default->{return value;}
        }
    }
    private static String normalizeDecimal(String value,Locale locale){
        String v=value.replace(" ","");int comma=v.lastIndexOf(','),dot=v.lastIndexOf('.');
        if(comma>=0&&dot>=0)return comma>dot?v.replace(".","").replace(',','.'):v.replace(",","");
        if(comma>=0)return v.replace(',','.');
        return v;
    }
}
