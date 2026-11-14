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

    @Column(length = 60)
    private String passwordHash;
    private int loginFailures;
    private java.time.Instant loginLockedUntil;

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String hash) { this.passwordHash = hash; }
    public boolean isLoginLocked() {
        return loginLockedUntil != null && java.time.Instant.now().isBefore(loginLockedUntil);
    }
    public void recordLoginFailure() {
        if (loginLockedUntil != null && !isLoginLocked()) loginFailures = 0;
        if (++loginFailures >= 5) loginLockedUntil = java.time.Instant.now().plusSeconds(900);
    }
    public void clearLoginFailures() { loginFailures = 0; loginLockedUntil = null; }

    private String fullName;
    @Column(unique = true)
    private String email;
    private java.time.Instant emailVerifiedAt;
    @Column(nullable = false)
    private java.time.Instant createdAt = java.time.Instant.now();

    public UserEntity(String username, Long telegramUserId, String fullName, String email) {
        this(username, telegramUserId);
        this.fullName = fullName;
        this.email = email;
        this.emailVerifiedAt = java.time.Instant.now();
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public java.time.Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public java.time.Instant getCreatedAt() { return createdAt; }

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
