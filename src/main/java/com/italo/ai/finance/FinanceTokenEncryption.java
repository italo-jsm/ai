package com.italo.ai.finance;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class FinanceTokenEncryption {
    private static final int IV_SIZE = 12;
    private static final String INVALID_KEY =
            "FINANCE_OAUTH_ENCRYPTION_KEY deve conter 32 bytes em Base64. Use a saída de openssl rand -base64 32.";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    FinanceTokenEncryption(String encodedKey) {
        if (encodedKey == null) throw new IllegalArgumentException(INVALID_KEY);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedKey.strip());
        } catch (IllegalArgumentException invalidEncoding) {
            throw new IllegalArgumentException(INVALID_KEY);
        }
        if (bytes.length != 32) throw new IllegalArgumentException(INVALID_KEY);
        key = new SecretKeySpec(bytes, "AES");
    }

    String encrypt(UUID userId, String kind, String token) {
        byte[] iv = new byte[IV_SIZE];
        random.nextBytes(iv);
        byte[] encrypted = transform(Cipher.ENCRYPT_MODE, userId, kind, iv, token.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv).put(encrypted).array());
    }

    String decrypt(UUID userId, String kind, String value) {
        var data = ByteBuffer.wrap(Base64.getDecoder().decode(value));
        byte[] iv = new byte[IV_SIZE];
        data.get(iv);
        byte[] encrypted = new byte[data.remaining()];
        data.get(encrypted);
        return new String(transform(Cipher.DECRYPT_MODE, userId, kind, iv, encrypted), StandardCharsets.UTF_8);
    }

    private byte[] transform(int mode, UUID userId, String kind, byte[] iv, byte[] input) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD((userId + ":" + kind).getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Falha ao proteger a credencial do Finance.", failure);
        }
    }
}
