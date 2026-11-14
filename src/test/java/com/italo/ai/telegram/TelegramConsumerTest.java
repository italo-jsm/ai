package com.italo.ai.telegram;

import com.italo.ai.chat.ChatService;
import com.italo.ai.telegram.model.TelegramUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TelegramConsumerTest {
    private final ChatService chat = mock(ChatService.class);
    private final TelegramBotClient bot = mock(TelegramBotClient.class);
    private final TelegramConsumer consumer = new TelegramConsumer(chat, bot);

    @Test void answersInOriginChatUsingSenderIdentity() {
        when(chat.chat("telegram:5123456789", "Olá")).thenReturn("Oi!");

        consumer.consume(update("Olá", false));

        var order = inOrder(chat, bot);
        order.verify(chat).chat("telegram:5123456789", "Olá");
        order.verify(bot).sendMessage(-1001234567890L, 7L, "Oi!");
    }

    @Test void ignoresNonTextMessagesBotsAndEdits() {
        consumer.consume(update(null, false));
        consumer.consume(update("  ", false));
        consumer.consume(update("Olá", true));
        consumer.consume(new TelegramUpdate(1L, null, update("Editada", false).message(), null, null));
        consumer.consume(new TelegramUpdate(1L,
                new TelegramUpdate.Message(7L, null, null, null, "Anônima", null), null, null, null));

        verifyNoInteractions(chat, bot);
    }

    @Test void rejectsProcessingFailureWithoutRequeueOrLeakingCause() {
        when(chat.chat(anyString(), anyString())).thenThrow(new IllegalStateException("segredo"));

        assertThatThrownBy(() -> consumer.consume(update("Olá", false)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Falha ao processar update do Telegram.").hasNoCause();
        verifyNoInteractions(bot);
    }

    @Test void rejectsSendFailureWithoutRegeneratingAnswer() {
        when(chat.chat(anyString(), anyString())).thenReturn("Oi!");
        doThrow(new IllegalStateException("token secreto")).when(bot).sendMessage(anyLong(), anyLong(), anyString());

        assertThatThrownBy(() -> consumer.consume(update("Olá", false)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class).hasNoCause();
        verify(chat).chat("telegram:5123456789", "Olá");
    }

    private TelegramUpdate update(String text, boolean isBot) {
        var from = new TelegramUpdate.User(5123456789L, isBot, null, null, null, null);
        var group = new TelegramUpdate.Chat(-1001234567890L, "supergroup", null, null, null, null);
        var message = new TelegramUpdate.Message(7L, from, group, null, text, null);
        return new TelegramUpdate(42L, message, null, null, null);
    }
}
