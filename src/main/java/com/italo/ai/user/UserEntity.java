package com.italo.ai.user;

import jakarta.persistence.*;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class UserEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 255)
    private String username;
    @Column(name = "telegram_user_id", unique = true)
    private Long telegramUserId;

    protected UserEntity() {}

    public UserEntity(String username, Long telegramUserId) {
        this.id = UUID.randomUUID();
        this.username = normalizeUsername(username);
        setTelegramUserId(telegramUserId);
    }

    public static String normalizeUsername(String username) {
        if (username == null) throw new IllegalArgumentException("Informe o nome de usuário.");
        String normalized = username.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 255)
            throw new IllegalArgumentException("O nome de usuário deve conter entre 1 e 255 caracteres.");
        return normalized;
    }

    public void setTelegramUserId(Long telegramUserId) {
        if (telegramUserId != null && telegramUserId <= 0)
            throw new IllegalArgumentException("O ID do usuário do Telegram deve ser positivo.");
        this.telegramUserId = telegramUserId;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public Long getTelegramUserId() { return telegramUserId; }
}
