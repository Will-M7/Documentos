package com.ejemplo.crudpersona.studio;

import java.util.Map;
import java.util.Set;

/** Reuses the existing official-route client. */
public final class FactilizaProvider implements LookupProvider {
    private final FactilizaClient client;
    public FactilizaProvider(){this(new FactilizaClient());}
    public FactilizaProvider(FactilizaClient client){this.client=client;}
    @Override public String name(){return "Factiliza";}
    @Override public Set<String> services(){return Set.of("DNI","RUC");}
    @Override public Map<String,String> query(String service,String identifier,String credential)throws Exception{return client.query(service,identifier,credential).data();}
}
