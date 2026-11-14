package com.italo.ai.telegram;

import com.italo.ai.telegram.model.TelegramUpdate;
import com.italo.ai.chat.ChatService;
import com.italo.ai.user.UserService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramConsumer.class);

    private final ChatService chatService;
    private final TelegramBotClient botClient;

    private final UserService users;
    private final TelegramRegistrationService registrations;

    public TelegramConsumer(ChatService chatService, TelegramBotClient botClient, UserService users, TelegramRegistrationService registrations) {
        this.chatService = chatService;
        this.botClient = botClient;
        this.users = users;
        this.registrations = registrations;
    }

    @RabbitListener(queues = "${app.telegram.queue}", messageConverter = "telegramMessageConverter")
    public void consume(TelegramUpdate update) {
        var message = update.message();
        if (message == null || message.text() == null || message.text().isBlank()
                || message.from() == null || message.from().id() == null
                || Boolean.TRUE.equals(message.from().bot())
                || message.chat() == null || message.chat().id() == null
                || message.messageId() == null) {
            return;
        }
        try {
            var user = users.findByTelegramUserId(message.from().id());
            if (user.isEmpty()) {
                botClient.sendMessage(message.chat().id(), message.messageId(),
                        registrations.handle(update));
                return;
            }
            if (java.util.Set.of("/start", "/entrar", "/cancelar", "/reenviar").contains(message.text().strip())) {
                botClient.sendMessage(message.chat().id(), message.messageId(),
                        "Sua conta já está cadastrada. Pode enviar sua pergunta.");
                return;
            }
            String answer = chatService.chat(user.get(), message.text());
            botClient.sendMessage(message.chat().id(), message.messageId(), answer);
            log.info("Update do Telegram respondido: updateId={}", update.updateId());
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            botClient.sendMessage(message.chat().id(), message.messageId(),
                    "Os dados já estão em uso ou o cadastro foi atualizado. Envie /start para continuar ou /cancelar para reiniciar.");
        } catch (RuntimeException failure) {
            log.warn("Falha ao responder update do Telegram: updateId={}, tipo={}",
                    update.updateId(), failure.getClass().getSimpleName());
            throw new AmqpRejectAndDontRequeueException("Falha ao processar update do Telegram.");
        }
    }
}
