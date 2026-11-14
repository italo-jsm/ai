package com.italo.ai.telegram;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "telegram_registration")
public class TelegramRegistration {
    enum Step { NAME, USERNAME, PASSWORD, EMAIL, CODE, LOGIN_USERNAME, LOGIN_PASSWORD }
    @Id Long telegramUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) Step step = Step.NAME;
    @Column(length = 60) String passwordHash;
    String fullName;
    String username;
    String email;
    String codeHash;
    Instant expiresAt;
    Instant lastSentAt;
    int attempts;
    Long lastUpdateId;

    protected TelegramRegistration() {}
    TelegramRegistration(Long telegramUserId) { this.telegramUserId = telegramUserId; }
}
