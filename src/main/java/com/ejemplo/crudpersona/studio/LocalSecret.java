package com.ejemplo.crudpersona.studio;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** API token lives outside SQLite and transfer packages. Windows uses per-user DPAPI. */
public final class LocalSecret {
    private final Path secret, keyFile;
    private final boolean windows=System.getProperty("os.name","").toLowerCase().contains("win");
    public LocalSecret(Path directory){secret=directory.resolve("factiliza.secret");keyFile=directory.resolve(".local-secret.key");}
    public boolean configured(){return Files.exists(secret);}
    public void save(String token)throws Exception{
        if(token==null||token.isBlank())throw new IllegalArgumentException("Escriba el token.");
        byte[] value=token.trim().getBytes(StandardCharsets.UTF_8);
        byte[] protectedData=windows?dpapi(value,true):aes(value,true);
        Files.write(secret,protectedData,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        restrict(secret);
    }
    public String load()throws Exception{
        if(!configured())return null;
        byte[] data=Files.readAllBytes(secret);
        return new String(windows?dpapi(data,false):aes(data,false),StandardCharsets.UTF_8);
    }
    public void delete()throws Exception{Files.deleteIfExists(secret);}
    private static void restrict(Path file){try{Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));}catch(Exception ignored){}}
    private byte[] aes(byte[] input,boolean encrypt)throws Exception{
        byte[] key;
        if(Files.exists(keyFile))key=Files.readAllBytes(keyFile);
        else if(encrypt){KeyGenerator g=KeyGenerator.getInstance("AES");g.init(256);key=g.generateKey().getEncoded();Files.write(keyFile,key,StandardOpenOption.CREATE_NEW);restrict(keyFile);}
        else throw new IllegalStateException("No se encuentra la clave local del token.");
        SecretKey k=new SecretKeySpec(key,"AES");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        if(encrypt){byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);cipher.init(Cipher.ENCRYPT_MODE,k,new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(input);byte[] out=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(encrypted,0,out,iv.length,encrypted.length);return out;}
        if(input.length<29)throw new IllegalArgumentException("Token local dañado.");byte[] iv=java.util.Arrays.copyOfRange(input,0,12);cipher.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,iv));return cipher.doFinal(input,12,input.length-12);
    }
    private static byte[] dpapi(byte[] input,boolean encrypt)throws Exception{
        String operation=encrypt?"Protect":"Unprotect";
        String script="$b=[Convert]::FromBase64String([Console]::In.ReadToEnd());$o=[Security.Cryptography.ProtectedData]::"+operation+"($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Out.Write([Convert]::ToBase64String($o))";
        Process process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).start();
        try(var out=process.getOutputStream()){out.write(Base64.getEncoder().encode(input));}
        if(!process.waitFor(15,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("DPAPI no respondió.");}
        if(process.exitValue()!=0)throw new IllegalStateException("No se pudo proteger el token con DPAPI.");
        return Base64.getDecoder().decode(process.getInputStream().readAllBytes());
    }
}
