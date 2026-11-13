package com.ejemplo.crudpersona.studio;

import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Non-secret metadata in SQLite; each token remains encrypted outside the database. */
public final class ApiCredentialStore {
    public record Credential(long id,String name,String provider,boolean enabled,String createdAt,String status,
                             String invalidMonth,int invalidStreak,long retryAfter,String lastUsed,
                             int requests,int successes,int failures){
        @Override public String toString(){return name+" · "+provider+" · "+(enabled?status:"deshabilitada")+" · "+requests+" consultas del mes";}
    }
    private final Path directory;
    private final String url;
    private final Clock clock;
    public ApiCredentialStore(Path directory)throws Exception{this(directory,Clock.systemDefaultZone());}
    ApiCredentialStore(Path directory,Clock clock)throws Exception{
        this.directory=directory;this.clock=clock;url="jdbc:sqlite:"+directory.resolve("studio.db");
        try(Connection c=open();Statement s=c.createStatement()){
            s.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,provider TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 1,created_at TEXT NOT NULL,status TEXT NOT NULL DEFAULT 'Disponible',invalid_month TEXT,invalid_streak INTEGER NOT NULL DEFAULT 0,retry_after INTEGER NOT NULL DEFAULT 0,last_used TEXT)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_usage(credential_id INTEGER NOT NULL,month TEXT NOT NULL,requests INTEGER NOT NULL DEFAULT 0,successes INTEGER NOT NULL DEFAULT 0,failures INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(credential_id,month))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_failure(credential_id INTEGER NOT NULL,occurred_at TEXT NOT NULL,month TEXT NOT NULL,category TEXT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_retired(name TEXT NOT NULL,provider TEXT NOT NULL,retired_at TEXT NOT NULL,invalid_month TEXT,invalid_streak INTEGER NOT NULL,failure_count INTEGER NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS api_credential_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
        }
        LocalSecret legacy=new LocalSecret(directory);
        if(legacy.configured()&&!legacyImported()){
            try{if(credentials().stream().noneMatch(c->c.name().equals("Factiliza anterior")))add("Factiliza anterior","Factiliza",legacy.load());markLegacyImported();}catch(Exception ignored){/* Keep the original protected file for manual recovery. */}
        }
    }
    private boolean legacyImported()throws SQLException{try(Connection c=open();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT 1 FROM api_credential_meta WHERE key='factiliza_legacy_imported'")){return r.next();}}
    private void markLegacyImported()throws SQLException{try(Connection c=open();Statement s=c.createStatement()){s.executeUpdate("INSERT OR REPLACE INTO api_credential_meta(key,value) VALUES('factiliza_legacy_imported','1')");}}
    private Connection open()throws SQLException{return DriverManager.getConnection(url);}
    private LocalSecret vault(long id){return new LocalSecret(directory,"credential-"+id+".secret");}
    public List<Credential> credentials()throws SQLException{
        List<Credential> out=new ArrayList<>();String month=YearMonth.now(clock).toString();
        String sql="SELECT c.*,COALESCE(u.requests,0) requests,COALESCE(u.successes,0) successes,COALESCE(u.failures,0) failures FROM api_credential c LEFT JOIN api_credential_usage u ON u.credential_id=c.id AND u.month=? ORDER BY CASE c.provider WHEN 'Factiliza' THEN 0 ELSE 1 END,c.id";
        try(Connection c=open();PreparedStatement s=c.prepareStatement(sql)){s.setString(1,month);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new Credential(r.getLong("id"),r.getString("name"),r.getString("provider"),r.getInt("enabled")==1,r.getString("created_at"),r.getString("status"),r.getString("invalid_month"),r.getInt("invalid_streak"),r.getLong("retry_after"),r.getString("last_used"),r.getInt("requests"),r.getInt("successes"),r.getInt("failures")));}}return out;
    }
    public Credential add(String name,String provider,String token)throws Exception{
        validate(name,provider);if(token==null||token.isBlank())throw new IllegalArgumentException("Escriba una clave API.");long id;
        try(Connection c=open();PreparedStatement s=c.prepareStatement("INSERT INTO api_credential(name,provider,created_at) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){s.setString(1,name.trim());s.setString(2,provider);s.setString(3,LocalDateTime.now(clock).toString());s.executeUpdate();try(ResultSet r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("No se creó la credencial.");id=r.getLong(1);}}
        try{vault(id).save(token);}catch(Exception e){try(Connection c=open();PreparedStatement s=c.prepareStatement("DELETE FROM api_credential WHERE id=?")){s.setLong(1,id);s.executeUpdate();}throw e;}
        return credentials().stream().filter(x->x.id()==id).findFirst().orElseThrow();
    }
    private static void validate(String name,String provider){if(name==null||name.isBlank()||!List.of("Factiliza","APIsPERU").contains(provider))throw new IllegalArgumentException("Nombre o proveedor inválido.");}
    public void rename(long id,String name)throws SQLException{if(name==null||name.isBlank())throw new IllegalArgumentException("Escriba un nombre.");try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE api_credential SET name=? WHERE id=?")){s.setString(1,name.trim());s.setLong(2,id);s.executeUpdate();}}
    public void enabled(long id,boolean enabled)throws SQLException{try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE api_credential SET enabled=? WHERE id=?")){s.setInt(1,enabled?1:0);s.setLong(2,id);s.executeUpdate();}}
    public void replace(long id,String token)throws Exception{vault(id).save(token);try(Connection c=open();PreparedStatement s=c.prepareStatement("UPDATE api_credential SET status='Disponible',invalid_month=NULL,invalid_streak=0,retry_after=0 WHERE id=?")){s.setLong(1,id);s.executeUpdate();}}
    public String token(long id)throws Exception{return vault(id).load();}
    public void delete(long id)throws Exception{Credential credential=credentials().stream().filter(x->x.id()==id).findFirst().orElseThrow();try(Connection c=open()){c.setAutoCommit(false);try(PreparedStatement s=c.prepareStatement("INSERT INTO api_credential_retired(name,provider,retired_at,invalid_month,invalid_streak,failure_count) VALUES(?,?,?,?,?,(SELECT COUNT(*) FROM api_credential_failure WHERE credential_id=?))")){s.setString(1,credential.name());s.setString(2,credential.provider());s.setString(3,LocalDateTime.now(clock).toString());s.setString(4,credential.invalidMonth());s.setInt(5,credential.invalidStreak());s.setLong(6,id);s.executeUpdate();}try(PreparedStatement s=c.prepareStatement("DELETE FROM api_credential_usage WHERE credential_id=?")){s.setLong(1,id);s.executeUpdate();}try(PreparedStatement s=c.prepareStatement("DELETE FROM api_credential_failure WHERE credential_id=?")){s.setLong(1,id);s.executeUpdate();}try(PreparedStatement s=c.prepareStatement("DELETE FROM api_credential WHERE id=?")){s.setLong(1,id);s.executeUpdate();}c.commit();}vault(id).delete();}
    public void record(long id,LookupRouter.Status status,long retryAfterSeconds)throws SQLException{
        String month=YearMonth.now(clock).toString(),now=LocalDateTime.now(clock).toString();boolean ok=status==LookupRouter.Status.SUCCESS||status==LookupRouter.Status.PARTIAL;boolean dispatched=status!=LookupRouter.Status.NO_INTERNET;
        try(Connection c=open()){c.setAutoCommit(false);
            try(PreparedStatement s=c.prepareStatement("INSERT INTO api_credential_usage(credential_id,month,requests,successes,failures) VALUES(?,?,?,?,?) ON CONFLICT(credential_id,month) DO UPDATE SET requests=requests+excluded.requests,successes=successes+excluded.successes,failures=failures+excluded.failures")){s.setLong(1,id);s.setString(2,month);s.setInt(3,dispatched?1:0);s.setInt(4,ok?1:0);s.setInt(5,ok?0:1);s.executeUpdate();}
            if(!ok)try(PreparedStatement s=c.prepareStatement("INSERT INTO api_credential_failure(credential_id,occurred_at,month,category) VALUES(?,?,?,?)")){s.setLong(1,id);s.setString(2,now);s.setString(3,month);s.setString(4,status.name());s.executeUpdate();}
            String previous=null;int streak=0;try(PreparedStatement s=c.prepareStatement("SELECT invalid_month,invalid_streak FROM api_credential WHERE id=?")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(r.next()){previous=r.getString(1);streak=r.getInt(2);}}}
            if(status==LookupRouter.Status.SUCCESS||status==LookupRouter.Status.PARTIAL){previous=null;streak=0;}
            else if(status==LookupRouter.Status.INVALID&&(!month.equals(previous))){streak=YearMonth.now(clock).minusMonths(1).toString().equals(previous)?streak+1:1;previous=month;}
            long retry=0;if(status==LookupRouter.Status.RATE_LIMIT)retry=Instant.now(clock).plusSeconds(Math.max(3600,retryAfterSeconds)).getEpochSecond();else if(status==LookupRouter.Status.TEMPORARY||status==LookupRouter.Status.TIMEOUT)retry=Instant.now(clock).plusSeconds(60).getEpochSecond();
            String state=switch(status){case SUCCESS->"Disponible";case PARTIAL->"Respuesta parcial";case INVALID->streak>=2?"Invalidez confirmada dos meses":"Credencial inválida";case RATE_LIMIT->"Límite informado";case TIMEOUT->"Tiempo agotado";case NO_INTERNET->"Sin conexión";case TEMPORARY->"Servicio temporalmente no disponible";case PERMISSION->"Permisos insuficientes";case NOT_FOUND->"Documento no encontrado";case UNSUPPORTED->"Servicio no habilitado";case BAD_RESPONSE->"Respuesta incorrecta";case NO_CREDENTIAL->"Sin credencial";};
            try(PreparedStatement s=c.prepareStatement("UPDATE api_credential SET status=?,invalid_month=?,invalid_streak=?,retry_after=?,last_used=? WHERE id=?")){s.setString(1,state);s.setString(2,previous);s.setInt(3,streak);s.setLong(4,retry);s.setString(5,now);s.setLong(6,id);s.executeUpdate();}c.commit();
        }
    }
}
