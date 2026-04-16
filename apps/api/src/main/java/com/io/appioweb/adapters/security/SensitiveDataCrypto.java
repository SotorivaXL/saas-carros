package com.io.appioweb.adapters.security;

import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SensitiveDataCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALG = "AES";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_SIZE = 12;
    private static final String PREFIX = "enc:v1:";

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SensitiveDataCrypto(@Value("${APP_DB_ENCRYPTION_KEY:}") String configuredKey) {
        String raw = safeTrim(configuredKey);
        if (raw.isBlank()) {
            throw new IllegalStateException("APP_DB_ENCRYPTION_KEY nao configurada. Defina uma chave forte para criptografar dados sensiveis no banco.");
        }
        this.key = new SecretKeySpec(deriveKey(raw), KEY_ALG);
    }

    public String encrypt(String plainText) {
        String value = safeTrim(plainText);
        if (value.isBlank()) return "";
        if (isEncrypted(value)) return value;
        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception ex) {
            throw new BusinessException("SENSITIVE_DATA_ENCRYPT_ERROR", "Falha ao criptografar dados sensiveis");
        }
    }

    public String decrypt(String cipherText) {
        String value = safeTrim(cipherText);
        if (value.isBlank()) return "";
        if (!isEncrypted(value)) return value;
        try {
            byte[] packed = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (packed.length <= IV_SIZE) {
                throw new BusinessException("SENSITIVE_DATA_DECRYPT_ERROR", "Payload criptografado invalido");
            }
            byte[] iv = new byte[IV_SIZE];
            byte[] encrypted = new byte[packed.length - IV_SIZE];
            System.arraycopy(packed, 0, iv, 0, IV_SIZE);
            System.arraycopy(packed, IV_SIZE, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("SENSITIVE_DATA_DECRYPT_ERROR", "Falha ao descriptografar dados sensiveis");
        }
    }

    private boolean isEncrypted(String value) {
        return value.startsWith(PREFIX);
    }

    private byte[] deriveKey(String raw) {
        try {
            byte[] decoded = tryDecodeBase64(raw);
            if (decoded != null && (decoded.length == 16 || decoded.length == 24 || decoded.length == 32)) {
                return decoded;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("APP_DB_ENCRYPTION_KEY invalida", ex);
        }
    }

    private byte[] tryDecodeBase64(String raw) {
        try {
            return Base64.getDecoder().decode(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
