package com.italo.ai.telegram;

import com.italo.ai.telegram.model.TelegramUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramConsumer {

    private static final Logger log = LoggerFactory.getLogger(TelegramConsumer.class);

    @RabbitListener(queues = "${app.telegram.queue}", messageConverter = "telegramMessageConverter")
    public void consume(TelegramUpdate update) {
        // Não registra texto, nomes ou outros dados pessoais da mensagem.
        log.info("Update do Telegram recebido: updateId={}", update.updateId());
    }
}
