package com.italo.ai.telegram;

import com.italo.ai.chat.ChatService;
import com.italo.ai.finance.FinanceTelegram;
import com.italo.ai.finance.FinanceTools;
import com.italo.ai.user.UserService;
import static com.italo.ai.support.TestUsers.ALICE;
import java.util.Optional;
import com.italo.ai.telegram.model.TelegramUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TelegramConsumerTest {
    private final ChatService chat = mock(ChatService.class);
    private final TelegramBotClient bot = mock(TelegramBotClient.class);
    private final UserService users = mock(UserService.class);
    private final TelegramRegistrationService registrations = mock(TelegramRegistrationService.class);
    private final FinanceTelegram finance = mock(FinanceTelegram.class);
    private final TelegramConsumer consumer = new TelegramConsumer(chat, bot, users, registrations, Optional.of(finance));

    @org.junit.jupiter.api.BeforeEach void registerUser() {
        when(users.findByTelegramUserId(5123456789L)).thenReturn(Optional.of(ALICE));
    }

    @Test void answersInOriginChatUsingSenderIdentity() {
        when(chat.chat(ALICE, "Olá")).thenReturn("Oi!");

        consumer.consume(update("Olá", false));

        var order = inOrder(chat, bot);
        order.verify(chat).chat(ALICE, "Olá");
        order.verify(bot).sendMessage(-1001234567890L, 7L, "Oi!");
    }

    @Test void naturalLanguageFinanceQuestionUsesToolsBoundToPrivateSender() {
        var tools = mock(FinanceTools.class);
        String question = "Qual é o saldo da minha conta corrente?";
        when(finance.toolsFor(ALICE)).thenReturn(tools);
        when(chat.chat(ALICE, question, tools)).thenReturn("Saldo de 50,25.");

        consumer.consume(privateUpdate(question));

        verify(bot).sendMessage(5123456789L, 7L, "Saldo de 50,25.");
    }

    @Test void sendsTheApplicationLoginLinkWhenAToolRequiresAuthorization() {
        var tools = mock(FinanceTools.class);
        String question = "Qual o saldo?";
        when(finance.toolsFor(ALICE)).thenReturn(tools);
        when(chat.chat(ALICE, question, tools)).thenReturn("Resposta do modelo");
        when(tools.needsLogin()).thenReturn(true);
        when(finance.loginMessage(ALICE)).thenReturn("Entre em https://ai.example/finance/oauth/start?ticket=exemplo");

        consumer.consume(privateUpdate(question));

        verify(bot).sendMessageWithoutPreview(5123456789L, 7L,
                "Entre em https://ai.example/finance/oauth/start?ticket=exemplo");
        verifyNoMoreInteractions(bot);
    }

    @Test void financeToolsAreUnavailableInGroups() {
        String question = "Qual o saldo?";
        when(chat.chat(ALICE, question)).thenReturn("Resposta sem dados do Finance");
        consumer.consume(update(question, false));

        verifyNoInteractions(finance);
        verify(chat).chat(ALICE, question);
    }

    private TelegramUpdate privateUpdate(String text) {
        var from = new TelegramUpdate.User(5123456789L, false, null, null, null, null);
        var privateChat = new TelegramUpdate.Chat(from.id(), "private", null, null, null, null);
        var message = new TelegramUpdate.Message(7L, from, privateChat, null, text, null);
        return new TelegramUpdate(42L, message, null, null, null);
    }

    @Test void ignoresNonTextMessagesBotsAndEdits() {
        consumer.consume(update(null, false));
        consumer.consume(update("  ", false));
        consumer.consume(update("Olá", true));
        consumer.consume(new TelegramUpdate(1L, null, update("Editada", false).message(), null, null));
        consumer.consume(new TelegramUpdate(1L,
                new TelegramUpdate.Message(7L, null, null, null, "Anônima", null), null, null, null));

        verifyNoInteractions(chat, bot, users);
    }

    @Test void rejectsProcessingFailureWithoutRequeueOrLeakingCause() {
        when(chat.chat(any(), anyString())).thenThrow(new IllegalStateException("segredo"));

        assertThatThrownBy(() -> consumer.consume(update("Olá", false)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Falha ao processar update do Telegram.").hasNoCause();
        verifyNoInteractions(bot);
    }

    @Test void rejectsSendFailureWithoutRegeneratingAnswer() {
        when(chat.chat(any(), anyString())).thenReturn("Oi!");
        doThrow(new IllegalStateException("token secreto")).when(bot).sendMessage(anyLong(), anyLong(), anyString());

        assertThatThrownBy(() -> consumer.consume(update("Olá", false)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class).hasNoCause();
        verify(chat).chat(ALICE, "Olá");
    }

    private TelegramUpdate update(String text, boolean isBot) {
        var from = new TelegramUpdate.User(5123456789L, isBot, null, null, null, null);
        var group = new TelegramUpdate.Chat(-1001234567890L, "supergroup", null, null, null, null);
        var message = new TelegramUpdate.Message(7L, from, group, null, text, null);
        return new TelegramUpdate(42L, message, null, null, null);
    }
    @Test void unknownTelegramUserReceivesRegistrationMessageWithoutCallingChat() {
        when(users.findByTelegramUserId(5123456789L)).thenReturn(Optional.empty());

        when(registrations.handle(any())).thenReturn("Abra o bot e envie /start.");
        consumer.consume(update("Olá", false));

        verify(bot).sendMessage(-1001234567890L, 7L,
                "Abra o bot e envie /start.");
        verifyNoInteractions(chat);
    }
}
