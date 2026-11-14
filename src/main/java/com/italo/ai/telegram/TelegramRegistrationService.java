package com.italo.ai.telegram;

import com.italo.ai.telegram.model.TelegramUpdate;
import com.italo.ai.user.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramRegistrationService {
    private final TelegramRegistrationRepository registrations;
    private final UserRepository users;
    private final RegistrationEmailSender emailSender;
    private final UserService userService;
    private final SecureRandom random = new SecureRandom();

    public TelegramRegistrationService(TelegramRegistrationRepository registrations,
            UserRepository users, RegistrationEmailSender emailSender, UserService userService) {
        this.registrations = registrations;
        this.users = users;
        this.emailSender = emailSender;
        this.userService = userService;
    }

    @Transactional
    public String handle(TelegramUpdate update) {
        var message = update.message();
        if (!"private".equals(message.chat().type()))
            return "Para se cadastrar, abra uma conversa privada com o bot e envie /start.";
        long id = message.from().id();
        String rawText = message.text();
        String text = rawText.strip();
        var pending = registrations.findForUpdate(id);
        if (text.equals("/cancelar")) {
            pending.ifPresent(registrations::delete);
            return "Cadastro cancelado. Envie /start para começar novamente.";
        }
        if (pending.isEmpty() && !text.equals("/start") && !text.equals("/entrar"))
            return "Já tem conta? Envie /entrar para vincular. Para criar sua conta, envie /start.";
        var registration = pending.orElseGet(() -> registrations.save(new TelegramRegistration(id)));
        if (text.equals("/entrar")) {
            registration.step = TelegramRegistration.Step.LOGIN_USERNAME;
            registration.passwordHash = null;
            registration.username = null;
            return prompt(registration);
        }
        if (text.equals("/start")) return prompt(registration);
        if (update.updateId() != null && registration.lastUpdateId != null
                && update.updateId() <= registration.lastUpdateId) return prompt(registration);
        registration.lastUpdateId = update.updateId();
        if (text.equals("/reenviar")) {
            if (registration.step != TelegramRegistration.Step.CODE) return prompt(registration);
            return sendCode(registration);
        }
        if (text.startsWith("/") && registration.step != TelegramRegistration.Step.PASSWORD
                && registration.step != TelegramRegistration.Step.LOGIN_PASSWORD) return "Use /start, /entrar, /cancelar ou /reenviar. " + prompt(registration);
        switch (registration.step) {
            case NAME -> {
                if (text.length() < 2 || text.length() > 255)
                    return "Informe seu nome completo, entre 2 e 255 caracteres.";
                registration.fullName = text;
                registration.step = TelegramRegistration.Step.USERNAME;
            }
            case USERNAME -> {
                if (!text.matches("[a-zA-Z0-9_]{3,32}"))
                    return "Escolha um nome de usuário com 3 a 32 letras, números ou underscore (_).";
                String username = UserEntity.normalizeUsername(text);
                if (users.existsByUsername(username)) return "Esse nome de usuário já está em uso. Se a conta é sua, use /entrar; ou escolha outro.";
                registration.username = username;
                registration.step = TelegramRegistration.Step.PASSWORD;
            }
            case PASSWORD -> {
                try {
                    registration.passwordHash = Passwords.hash(rawText);
                } catch (IllegalArgumentException invalid) {
                    return invalid.getMessage();
                }
                registration.step = TelegramRegistration.Step.EMAIL;
            }
            case LOGIN_USERNAME -> {
                try {
                    registration.username = UserEntity.normalizeUsername(text);
                } catch (IllegalArgumentException invalid) {
                    return invalid.getMessage();
                }
                registration.step = TelegramRegistration.Step.LOGIN_PASSWORD;
            }
            case LOGIN_PASSWORD -> {
                var linked = userService.linkTelegram(registration.username, id, rawText);
                if (linked.isEmpty()) return "Credenciais inválidas, conta indisponível ou temporariamente bloqueada. "
                        + "Tente novamente ou use /cancelar. Após cinco falhas, aguarde 15 minutos.";
                registrations.delete(registration);
                return "Conta vinculada ao Telegram! Pode enviar sua pergunta.";
            }
            case EMAIL -> {
                String email = text.toLowerCase(Locale.ROOT);
                if (email.length() > 255 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                    return "Informe um endereço de e-mail válido.";
                if (users.existsByEmail(email)) return "Esse e-mail já está em uso. Informe outro ou use /cancelar.";
                registration.email = email;
                return sendCode(registration);
            }
            case CODE -> {
                if (registration.expiresAt == null || !Instant.now().isBefore(registration.expiresAt))
                    return "O código expirou. Use /reenviar para receber outro.";
                if (registration.attempts >= 5)
                    return "Limite de tentativas atingido. Use /reenviar para receber outro código.";
                registration.attempts++;
                if (!MessageDigest.isEqual(hash(id, text).getBytes(StandardCharsets.UTF_8),
                        registration.codeHash.getBytes(StandardCharsets.UTF_8)))
                    return "Código incorreto. Tentativas restantes: " + (5 - registration.attempts) + ".";
                if (users.findByTelegramUserId(id).isPresent()) {
                    registrations.delete(registration);
                    return "Sua conta já está cadastrada. Pode enviar sua pergunta.";
                }
                if (users.existsByUsername(registration.username) || users.existsByEmail(registration.email)) {
                    registrations.delete(registration);
                    return "O nome de usuário ou e-mail já está em uso. Envie /start para reiniciar o cadastro.";
                }
                if (registration.passwordHash == null) {
                    registration.step = TelegramRegistration.Step.PASSWORD;
                    return prompt(registration);
                }
                var user = new UserEntity(registration.username, id, registration.fullName, registration.email);
                user.setPasswordHash(registration.passwordHash);
                users.saveAndFlush(user);
                registrations.delete(registration);
                return "E-mail confirmado. Cadastro concluído! Pode enviar sua pergunta.";
            }
        }
        return prompt(registration);
    }

    private String sendCode(TelegramRegistration registration) {
        var now = Instant.now();
        if (registration.lastSentAt != null && now.isBefore(registration.lastSentAt.plusSeconds(60)))
            return "Aguarde 60 segundos entre envios do código.";
        String code = "%06d".formatted(random.nextInt(1_000_000));
        try {
            emailSender.send(registration.email, code);
        } catch (MailException failure) {
            return "Não foi possível enviar o e-mail. " +
                    (registration.step == TelegramRegistration.Step.CODE
                            ? "Tente /reenviar novamente." : "Informe seu e-mail novamente para tentar.");
        }
        registration.codeHash = hash(registration.telegramUserId, code);
        registration.expiresAt = now.plusSeconds(600);
        registration.lastSentAt = now;
        registration.attempts = 0;
        registration.step = TelegramRegistration.Step.CODE;
        return "Enviamos um código para seu e-mail. Digite os seis números aqui. "
                + "O código vale por 10 minutos. Use /reenviar ou /cancelar se precisar.";
    }

    private String prompt(TelegramRegistration registration) {
        return switch (registration.step) {
            case NAME -> "Vamos criar sua conta. Qual é seu nome completo? Já tem conta na web? Use /entrar. Use /cancelar para desistir.";
            case USERNAME -> "Escolha seu nome de usuário (3 a 32 letras, números ou underscore).";
            case PASSWORD -> "Crie uma senha com ao menos 8 caracteres (máximo de 72 bytes UTF-8).";
            case LOGIN_USERNAME -> "Qual é seu nome de usuário da web?";
            case LOGIN_PASSWORD -> "Informe a senha da sua conta para vincular ao Telegram.";
            case EMAIL -> "Qual é seu e-mail? Enviaremos um código de confirmação.";
            case CODE -> "Digite o código enviado ao seu e-mail. Use /reenviar ou /cancelar se precisar.";
        };
    }

    private String hash(long id, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((id + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
