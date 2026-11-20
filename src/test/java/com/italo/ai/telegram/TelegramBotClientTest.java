package com.italo.ai.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramBotClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.telegram.org");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TelegramBotClient client = new TelegramBotClient(builder.build(), "test-token");

    @Test void sendsPlainTextReplyWithLongChatId() {
        server.expect(requestTo("https://api.telegram.org/bottest-token/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"chat_id":-1001234567890,"text":"Olá **mundo**!",
                        "reply_parameters":{"message_id":7,"allow_sending_without_reply":true}}
                        """))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":8}}", MediaType.APPLICATION_JSON));

        client.sendMessage(-1001234567890L, 7L, "Olá **mundo**!");

        server.verify();
    }

    @Test void formatsAnswerTablesAndBoldUsingUtf16Offsets() {
        server.expect(anything())
                .andExpect(jsonPath("$.text").value(
                        "😀 Contas disponíveis\n\n• Itaú & Cia\n  Tipo: Conta corrente\n  Saldo: -180,00 R$"))
                .andExpect(jsonPath("$.entities[0].type").value("bold"))
                .andExpect(jsonPath("$.entities[0].offset").value(3))
                .andExpect(jsonPath("$.entities[0].length").value(18))
                .andExpect(jsonPath("$.entities[1].offset").value(25))
                .andExpect(jsonPath("$.entities[1].length").value(10))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        client.sendAnswer(1L, 7L, """
                😀 **Contas disponíveis**

                | Nome | Tipo | Saldo |
                | --- | --- | --- |
                | **Itaú & Cia** | Conta corrente | -180,00 R$ |""");

        server.verify();
    }

    @Test void keepsBoldAcrossMessageParts() {
        String firstPart = "a".repeat(4095);
        server.expect(anything()).andExpect(jsonPath("$.text").value(firstPart))
                .andExpect(jsonPath("$.entities[0].length").value(4095))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andExpect(jsonPath("$.text").value("😀fim"))
                .andExpect(jsonPath("$.entities[0].offset").value(0))
                .andExpect(jsonPath("$.entities[0].length").value(5))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        client.sendAnswer(1L, 7L, "**" + firstPart + "😀fim**");

        server.verify();
    }

    @Test void splitsLongResponsesWithoutBreakingEmoji() {
        String firstPart = "a".repeat(4095);
        server.expect(anything()).andExpect(jsonPath("$.text").value(firstPart))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        server.expect(anything()).andExpect(jsonPath("$.text").value("😀fim"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        client.sendMessage(1L, 7L, firstPart + "😀fim");

        server.verify();
    }

    @Test void sanitizesHttpErrorsAndStopsSending() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret details"));

        assertThatThrownBy(() -> client.sendMessage(1L, 7L, "a".repeat(5000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Falha ao enviar resposta ao Telegram.").hasNoCause();
        server.verify();
    }

    @Test void rejectsUnconfirmedDelivery() {
        server.expect(anything()).andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendMessage(1L, 7L, "Oi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Telegram não confirmou o envio da resposta.");
        server.verify();
    }

    @Test void rejectsMissingTokenOrEmptyAnswerBeforeSending() {
        assertThatThrownBy(() -> new TelegramBotClient(builder.build(), "").sendMessage(1L, 7L, "Oi"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TELEGRAM_BOT_TOKEN");
        assertThatThrownBy(() -> client.sendMessage(1L, 7L, " "))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
