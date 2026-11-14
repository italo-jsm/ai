package com.italo.ai.telegram;

import com.italo.ai.config.TelegramRabbitConfiguration;
import com.italo.ai.telegram.model.TelegramUpdate;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramMessageConversionTest {

    private final TelegramRabbitConfiguration configuration = new TelegramRabbitConfiguration();

    @Test
    void convertsRealQueueShapeWithoutSpringTypeHeaders() {
        var update = convert("""
                {"update_id":9123456789,"message":{
                  "message_id":25,
                  "from":{"id":5123456789,"is_bot":false,"first_name":"Teste",
                    "last_name":"Silva","language_code":"pt-br"},
                  "chat":{"id":-1001234567890,"first_name":"Teste","type":"supergroup"},
                  "date":1794672000,"text":"Olá!","entities":[]},"future_field":true}
                """);

        assertThat(update.updateId()).isEqualTo(9123456789L);
        assertThat(update.message().messageId()).isEqualTo(25L);
        assertThat(update.message().from().id()).isEqualTo(5123456789L);
        assertThat(update.message().from().bot()).isFalse();
        assertThat(update.message().from().firstName()).isEqualTo("Teste");
        assertThat(update.message().from().lastName()).isEqualTo("Silva");
        assertThat(update.message().from().languageCode()).isEqualTo("pt-br");
        assertThat(update.message().chat().id()).isEqualTo(-1001234567890L);
        assertThat(update.message().date()).isEqualTo(1794672000L);
        assertThat(update.message().text()).isEqualTo("Olá!");
        assertThatCode(() -> new TelegramConsumer().consume(update)).doesNotThrowAnyException();
    }

    @Test
    void acceptsMessagesWithoutTextOrSenderAndEditedMessages() {
        var update = convert("""
                {"update_id":42,"edited_message":{"message_id":10,
                  "chat":{"id":-1001234567890,"type":"channel","title":"Canal"},
                  "date":1794672000,"caption":"Imagem","photo":[{"file_id":"abc"}]}}
                """);
        assertThat(update.message()).isNull();
        assertThat(update.editedMessage().text()).isNull();
        assertThat(update.editedMessage().from()).isNull();
        assertThat(update.editedMessage().caption()).isEqualTo("Imagem");
        assertThatCode(() -> new TelegramConsumer().consume(update)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> convert("{broken"))
                .isInstanceOf(MessageConversionException.class);
    }

    private TelegramUpdate convert(String json) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(TelegramUpdate.class);
        return (TelegramUpdate) configuration.telegramMessageConverter().fromMessage(
                new Message(json.getBytes(StandardCharsets.UTF_8), properties));
    }
}
