package com.ejemplo.crudpersona.studio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Exact routes and response envelope from Factiliza DNI/RUC consultation docs. */
public final class FactilizaClient {
    public record Result(String type,String number,Map<String,String> data,String message){}
    private static final URI BASE=URI.create("https://api.factiliza.com/v1/");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ObjectMapper JSON=new ObjectMapper();
    public Result query(String type,String number,String token)throws Exception{
        String segment=switch(type){case "DNI"->"dni";case "RUC"->"ruc";default->throw new IllegalArgumentException("Tipo de consulta no admitido.");};
        int expected=type.equals("DNI")?8:11;
        if(number==null||!number.matches("\\d{"+expected+"}"))throw new IllegalArgumentException(type+" debe tener "+expected+" dígitos.");
        if(token==null||token.isBlank())throw new IllegalArgumentException("Configure el token de Factiliza.");
        HttpRequest request=HttpRequest.newBuilder(BASE.resolve(segment+"/info/"+number)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).GET().build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
        return parse(type,number,response.statusCode(),response.body());
    }
    public static Result parse(String type,String number,int code,String body)throws IOException{
        JsonNode root;
        try{root=JSON.readTree(body);}catch(Exception e){throw new IOException("Factiliza devolvió una respuesta que no es JSON.",e);}
        if(root==null||!root.isObject())throw new IOException("Respuesta incompleta de Factiliza.");
        String message=root.path("message").asText("");
        if(code!=200||!root.path("success").asBoolean(false))throw new IOException("Factiliza: "+(message.isBlank()?"HTTP "+code:message));
        JsonNode data=root.path("data");
        if(!data.isObject()||data.path("numero").asText("").isBlank())throw new IOException("Factiliza devolvió datos incompletos.");
        Map<String,String> fields=new LinkedHashMap<>();
        data.fields().forEachRemaining(e->{if(e.getValue().isValueNode()&&!e.getValue().isNull())fields.put(e.getKey(),e.getValue().asText());});
        if(type.equals("DNI")&&fields.getOrDefault("nombres","").isBlank())throw new IOException("Factiliza no devolvió nombres.");
        if(type.equals("RUC")&&fields.getOrDefault("nombre_o_razon_social","").isBlank())throw new IOException("Factiliza no devolvió razón social.");
        return new Result(type,number,fields,message);
    }
}
