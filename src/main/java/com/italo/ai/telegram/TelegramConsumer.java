package com.italo.ai.telegram;

import com.italo.ai.telegram.model.TelegramUpdate;
import com.italo.ai.chat.ChatService;
import com.italo.ai.finance.FinanceTelegram;
import com.italo.ai.user.UserService;
import java.util.Optional;
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
    private final Optional<FinanceTelegram> finance;

    public TelegramConsumer(ChatService chatService, TelegramBotClient botClient, UserService users,
                            TelegramRegistrationService registrations, Optional<FinanceTelegram> finance) {
        this.chatService = chatService;
        this.botClient = botClient;
        this.users = users;
        this.registrations = registrations;
        this.finance = finance;
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
            var owner = user.get();
            boolean privateChat = "private".equals(message.chat().type()) && message.from().id().equals(message.chat().id());
            String answer;
            if (privateChat && finance.isPresent()) {
                var integration = finance.get();
                var tools = integration.toolsFor(owner);
                answer = chatService.chatFromTelegram(owner, message.text(), message.chat().id(), tools);
                if (tools.needsLogin()) {
                    botClient.sendMessageWithoutPreview(message.chat().id(), message.messageId(),
                            integration.loginMessage(owner));
                    return;
                }
            } else {
                answer = chatService.chatFromTelegram(owner, message.text(), message.chat().id(), null);
            }
            botClient.sendAnswer(message.chat().id(), message.messageId(), answer);
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
