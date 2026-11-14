package com.italo.ai.telegram;

import com.italo.ai.telegram.model.TelegramUpdate;
import com.italo.ai.chat.ChatService;
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

    public TelegramConsumer(ChatService chatService, TelegramBotClient botClient) {
        this.chatService = chatService;
        this.botClient = botClient;
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
            String answer = chatService.chat("telegram:" + message.from().id(), message.text());
            botClient.sendMessage(message.chat().id(), message.messageId(), answer);
            log.info("Update do Telegram respondido: updateId={}", update.updateId());
        } catch (RuntimeException failure) {
            log.warn("Falha ao responder update do Telegram: updateId={}, tipo={}",
                    update.updateId(), failure.getClass().getSimpleName());
            throw new AmqpRejectAndDontRequeueException("Falha ao processar update do Telegram.");
        }
    }
}
