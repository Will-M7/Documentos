package pe.estudiodocumental.consultas.infraestructura;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra los tokens de los proveedores con AES-256-GCM. La clave se deriva de {@code APP_CLAVE_MAESTRA}, que vive en
 * el servidor y nunca en la base: un respaldo robado de la base no expone los tokens.
 */
@Component
public class CifradoTokens {

    private static final int BYTES_IV = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKeySpec clave;
    private final SecureRandom azar = new SecureRandom();

    CifradoTokens(@Value("${app.seguridad.clave-maestra}") String claveMaestra) {
        if (claveMaestra == null || claveMaestra.length() < 32) {
            throw new IllegalStateException("APP_CLAVE_MAESTRA debe tener al menos 32 caracteres.");
        }
        try {
            byte[] material =
                    MessageDigest.getInstance("SHA-256").digest(claveMaestra.getBytes(StandardCharsets.UTF_8));
            this.clave = new SecretKeySpec(material, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String cifrar(String texto) {
        try {
            byte[] iv = new byte[BYTES_IV];
            azar.nextBytes(iv);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, iv));
            byte[] cifrado = cifrador.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(iv.length + cifrado.length)
                            .put(iv)
                            .put(cifrado)
                            .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el token.", e);
        }
    }

    public String descifrar(String guardado) {
        try {
            byte[] datos = Base64.getDecoder().decode(guardado);
            Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
            cifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, datos, 0, BYTES_IV));
            return new String(cifrador.doFinal(datos, BYTES_IV, datos.length - BYTES_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "No se pudo descifrar el token: ¿cambió APP_CLAVE_MAESTRA? Vuelva a registrar la credencial.", e);
        }
    }
}
