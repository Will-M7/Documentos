package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Exact routes and response envelope from Factiliza DNI/RUC consultation docs. */
public final class FactilizaClient {
    public static final class HttpStatusException extends IOException {
        private final int statusCode;private final long retryAfterSeconds;
        public HttpStatusException(int statusCode,long retryAfterSeconds,String message){super(message);this.statusCode=statusCode;this.retryAfterSeconds=retryAfterSeconds;}
        public int statusCode(){return statusCode;}
        public long retryAfterSeconds(){return retryAfterSeconds;}
    }
    public record Result(String type,String number,Map<String,String> data,String message){}
    private static final URI BASE=URI.create("https://api.factiliza.com/v1/");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ObjectMapper JSON=new ObjectMapper();
    public Result query(String type,String number,String token)throws Exception{
        String segment=switch(type){case "DNI"->"dni/info";case "RUC"->"ruc/info";case "RUC - Representantes"->"ruc/representante";case "RUC - Establecimientos"->"ruc/anexo";case "CEE"->"cee/info";default->throw new IllegalArgumentException("Tipo de consulta no admitido.");};
        int expected=type.equals("DNI")?8:type.equals("CEE")?9:11;
        if(number==null||!number.matches("\\d{"+expected+"}"))throw new IllegalArgumentException(type+" debe tener "+expected+" dígitos.");
        if(token==null||token.isBlank())throw new IllegalArgumentException("Configure el token de Factiliza.");
        HttpRequest request=HttpRequest.newBuilder(BASE.resolve(segment+"/"+number)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).GET().build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        try{return parse(type,number,response.statusCode(),response.body());}catch(HttpStatusException e){throw new HttpStatusException(e.statusCode(),retryAfter(response.headers()),e.getMessage());}
    }
    public static Result parse(String type,String number,int code,String body)throws IOException{
        JsonNode root;
        try{root=JSON.readTree(body);}catch(Exception e){throw new IOException("Factiliza devolvió una respuesta que no es JSON.",e);}
        if(root==null||!root.isObject())throw new IOException("Respuesta incompleta de Factiliza.");
        String message=root.path("message").asText("");
        if(code!=200||root.has("success")&&!root.path("success").asBoolean(false))throw new HttpStatusException(code!=200?code:root.path("status").asInt(400),0,"Factiliza: "+(message.isBlank()?"HTTP "+code:message));
        JsonNode data=root.path("data");
        if(!data.isObject()&&!data.isArray()||List.of("DNI","RUC").contains(type)&&data.path("numero").asText("").isBlank())throw new IOException("Factiliza devolvió datos incompletos.");
        Map<String,String> fields=new LinkedHashMap<>();
        flatten("data",data,fields);
        if(data.isObject())data.fields().forEachRemaining(e->{if(e.getValue().isValueNode()&&!e.getValue().isNull())fields.put(e.getKey(),e.getValue().asText());});
        return new Result(type,number,fields,message);
    }
    private static void flatten(String path,JsonNode node,Map<String,String> fields){
        if(node.isObject())node.fields().forEachRemaining(e->flatten(path+"."+e.getKey(),e.getValue(),fields));
        else if(node.isArray())for(int i=0;i<node.size();i++)flatten(path+"["+i+"]",node.get(i),fields);
        else if(!node.isNull())fields.put(path,node.asText());
    }
    public static long retryAfter(HttpHeaders headers){String value=headers.firstValue("Retry-After").orElse("").trim();try{return Math.max(0,Long.parseLong(value));}catch(Exception ignored){}try{return Math.max(0,Duration.between(Instant.now(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds());}catch(Exception ignored){return 0;}}
}
