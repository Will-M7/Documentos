package com.ejemplo.crudpersona.studio;

import java.util.Map;
import java.util.Set;

public interface LookupProvider {
    String name();
    Set<String> services();
    Map<String,String> query(String service,String identifier,String credential)throws Exception;
}
