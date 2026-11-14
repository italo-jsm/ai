package com.italo.ai.user;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class Passwords {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    private static final String DUMMY = ENCODER.encode(java.util.UUID.randomUUID().toString());
    private Passwords() {}

    public static String hash(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("A senha deve ter ao menos 8 caracteres e no máximo 72 bytes UTF-8.");
        return ENCODER.encode(password);
    }

    public static boolean matches(String password, String hash) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        boolean matches = ENCODER.matches(password, hash == null ? DUMMY : hash);
        return hash != null && matches;
    }
}
