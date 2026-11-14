package com.italo.ai.telegram;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class RegistrationEmailSender {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public RegistrationEmailSender(ObjectProvider<JavaMailSender> sender,
            @Value("${app.registration.mail-from:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public void send(String email, String code) {
        var mail = sender.getIfAvailable();
        if (mail == null || from.isBlank()) throw new MailSendException("SMTP não configurado.");
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Confirme seu cadastro");
        message.setText("Seu código de confirmação é: " + code + "\nVálido por 10 minutos.");
        mail.send(message);
    }
}
