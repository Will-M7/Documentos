package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Contract from https://dniruc.apisperu.com/doc/swagger (consultation API only). */
public final class ApisPeruProvider implements LookupProvider {
    private static final String BASE="https://dniruc.apisperu.com/api/v1/";
    private static final ObjectMapper JSON=new ObjectMapper();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    @Override public String name(){return "APIsPERU";}
    @Override public Set<String> services(){return Set.of("DNI","RUC");}
    @Override public Map<String,String> query(String service,String identifier,String credential)throws Exception{
        if(!services().contains(service))throw new IllegalArgumentException("Servicio no admitido.");
        if(credential==null||credential.isBlank())throw new IllegalArgumentException("Falta una credencial.");
        int length="DNI".equals(service)?8:11;if(identifier==null||!identifier.matches("\\d{"+length+"}"))throw new IllegalArgumentException("Número de documento inválido.");
        String segment=service.toLowerCase(Locale.ROOT);
        URI uri=URI.create(BASE+segment+"/"+identifier+"?token="+URLEncoder.encode(credential,StandardCharsets.UTF_8));
        HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        long retry=FactilizaClient.retryAfter(response.headers());
        return parse(service,response.statusCode(),response.body(),retry);
    }
    public static Map<String,String> parse(String service,int status,String body,long retryAfter)throws IOException{
        JsonNode root;try{root=JSON.readTree(body);}catch(Exception e){throw new IOException("APIsPERU devolvió JSON incorrecto.",e);}
        if(root==null||!root.isObject())throw new IOException("APIsPERU devolvió una respuesta incorrecta.");
        if(status!=200||root.has("success")&&!root.path("success").asBoolean(true))throw new FactilizaClient.HttpStatusException(status==200?root.path("status").asInt(400):status,retryAfter,"APIsPERU: "+root.path("message").asText("La consulta no se completó."));
        Map<String,String> raw=new LinkedHashMap<>();flatten("",root,raw);
        if("DNI".equals(service)&&!raw.containsKey("dni")||"RUC".equals(service)&&!raw.containsKey("ruc"))throw new IOException("APIsPERU devolvió datos incompletos.");
        return raw;
    }
    private static void flatten(String prefix,JsonNode node,Map<String,String> out){
        if(node.isObject())node.fields().forEachRemaining(e->flatten(prefix.isBlank()?e.getKey():prefix+"."+e.getKey(),e.getValue(),out));
        else if(node.isArray())for(int i=0;i<node.size();i++)flatten(prefix+"["+i+"]",node.get(i),out);
        else if(!node.isNull())out.put(prefix,node.asText());
    }
}
