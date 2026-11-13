package com.ejemplo.crudpersona.studio;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.*;

/** Bounded provider fallback. A failed lookup never changes document fields. */
public final class LookupRouter {
    public enum Status {SUCCESS,PARTIAL,NO_CREDENTIAL,NO_INTERNET,TIMEOUT,TEMPORARY,RATE_LIMIT,INVALID,PERMISSION,UNSUPPORTED,NOT_FOUND,BAD_RESPONSE}
    public record Result(String service,String identifier,String provider,Map<String,String> values,
                         Set<String> available,Set<String> unavailable,Status status,String error,Map<String,String> raw){
        public boolean usable(){return status==Status.SUCCESS||status==Status.PARTIAL;}
    }
    private final ApiCredentialStore credentials;
    private final Map<String,LookupProvider> providers=new LinkedHashMap<>();
    public LookupRouter(ApiCredentialStore credentials,Collection<LookupProvider> providers){this.credentials=credentials;for(LookupProvider provider:providers)this.providers.put(provider.name(),provider);}
    public Result query(String service,String identifier)throws Exception{return query(service,identifier,null);}
    public Result querySpecific(String service,String identifier,long credentialId)throws Exception{return query(service,identifier,credentialId);}
    private Result query(String service,String identifier,Long requestedId)throws Exception{
        int length="DNI".equals(service)?8:"RUC".equals(service)?11:0;
        if(length==0||identifier==null||!identifier.matches("\\d{"+length+"}"))throw new IllegalArgumentException("Número de documento inválido.");
        List<ApiCredentialStore.Credential> options=new ArrayList<>(credentials.credentials());options.removeIf(c->requestedId!=null?c.id()!=requestedId:!c.enabled()||c.status().contains("inválida")||c.status().contains("Invalidez confirmada")||c.retryAfter()>Instant.now().getEpochSecond());
        options.removeIf(c->!providers.containsKey(c.provider())||!providers.get(c.provider()).services().contains(service));
        options.sort(Comparator.comparingInt((ApiCredentialStore.Credential c)->c.provider().equals("Factiliza")?0:1).thenComparingInt(ApiCredentialStore.Credential::requests).thenComparingLong(ApiCredentialStore.Credential::id));
        if(options.isEmpty())return empty(service,identifier,Status.NO_CREDENTIAL,"No hay credenciales habilitadas; complete los campos manualmente.");
        Status last=Status.NO_CREDENTIAL;String reason="No se obtuvo información.";Set<String> skippedProviders=new HashSet<>();int attempts=0;
        for(ApiCredentialStore.Credential credential:options){
            if(attempts>=4||skippedProviders.contains(credential.provider()))continue;
            LookupProvider provider=providers.get(credential.provider());String token;
            try{token=credentials.token(credential.id());}catch(Exception e){last=Status.BAD_RESPONSE;reason="No se pudo leer la credencial protegida.";continue;}
            if(token==null||token.isBlank()){last=Status.BAD_RESPONSE;reason="La credencial protegida está vacía.";continue;}
            attempts++;
            try{
                Map<String,String> raw=provider.query(service,identifier,token);
                Map<String,String> values=ApiFieldCatalog.normalize(provider.name(),service,raw);
                Set<String> expected=new LinkedHashSet<>();for(ApiFieldCatalog.Field field:ApiFieldCatalog.fields(service))expected.add(field.key());
                Set<String> unavailable=new LinkedHashSet<>(expected);unavailable.removeAll(values.keySet());
                Set<String> missingSupported=new LinkedHashSet<>(ApiFieldCatalog.supportedKeys(provider.name(),service));missingSupported.removeAll(values.keySet());
                Status status=values.isEmpty()?Status.BAD_RESPONSE:missingSupported.isEmpty()?Status.SUCCESS:Status.PARTIAL;
                credentials.record(credential.id(),status,0);
                if(status==Status.BAD_RESPONSE){last=status;reason="El proveedor devolvió una respuesta sin datos utilizables.";skippedProviders.add(provider.name());continue;}
                return new Result(service,identifier,provider.name(),Map.copyOf(values),Set.copyOf(values.keySet()),Set.copyOf(unavailable),status,status==Status.PARTIAL?"Algunos campos no están disponibles.":"",Map.copyOf(raw));
            }catch(Exception failure){
                Status status=classify(failure);long retry=failure instanceof FactilizaClient.HttpStatusException http?http.retryAfterSeconds():0;
                credentials.record(credential.id(),status,retry);last=status;reason=message(status);
                if(status==Status.NO_INTERNET||status==Status.NOT_FOUND)break;
                if(status==Status.RATE_LIMIT||status==Status.TIMEOUT||status==Status.TEMPORARY||status==Status.PERMISSION||status==Status.UNSUPPORTED||status==Status.BAD_RESPONSE)skippedProviders.add(provider.name());
            }
        }
        return empty(service,identifier,last,reason+" Puede continuar manualmente.");
    }
    private static Result empty(String service,String identifier,Status status,String reason){Set<String> missing=new LinkedHashSet<>();for(ApiFieldCatalog.Field field:ApiFieldCatalog.fields(service))missing.add(field.key());return new Result(service,identifier,"",Map.of(),Set.of(),Set.copyOf(missing),status,reason,Map.of());}
    private static Status classify(Throwable failure){
        if(failure instanceof FactilizaClient.HttpStatusException http)return switch(http.statusCode()){case 401->Status.INVALID;case 403->Status.PERMISSION;case 404->Status.NOT_FOUND;case 429->Status.RATE_LIMIT;case 408,504->Status.TIMEOUT;case 501->Status.UNSUPPORTED;default->http.statusCode()>=500?Status.TEMPORARY:Status.BAD_RESPONSE;};
        if(failure instanceof HttpTimeoutException)return Status.TIMEOUT;
        if(failure instanceof UnknownHostException||failure instanceof ConnectException)return Status.NO_INTERNET;
        if(failure instanceof IOException io&&io.getCause()!=null)return classify(io.getCause());
        return Status.BAD_RESPONSE;
    }
    private static String message(Status status){return switch(status){case NO_INTERNET->"Sin conexión a Internet.";case TIMEOUT->"La consulta agotó el tiempo de espera.";case TEMPORARY->"El proveedor no está disponible temporalmente.";case RATE_LIMIT->"Se alcanzó un límite de consultas.";case INVALID->"La credencial fue rechazada.";case PERMISSION->"La credencial no tiene permisos suficientes.";case UNSUPPORTED->"El servicio no está habilitado.";case NOT_FOUND->"Documento no encontrado.";case PARTIAL->"Respuesta parcial.";case BAD_RESPONSE->"Respuesta incorrecta o incompleta.";case NO_CREDENTIAL->"Sin credenciales.";case SUCCESS->"Consulta correcta.";};}
}
