package com.italo.ai.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class TelegramBotClient {
    private final RestClient client;
    private final String token;

    public TelegramBotClient(RestClient client, String token) {
        this.client = client;
        this.token = token;
    }

    public void sendMessage(long chatId, long replyToMessageId, String text) {
        send(chatId, replyToMessageId, TelegramMessage.plainText(text), false);
    }

    public void sendAnswer(long chatId, long replyToMessageId, String text) {
        send(chatId, replyToMessageId, TelegramMessageFormatter.format(text), false);
    }

    public void sendMessageWithoutPreview(long chatId, long replyToMessageId, String text) {
        send(chatId, replyToMessageId, TelegramMessage.plainText(text), true);
    }

    private void send(long chatId, long replyToMessageId, TelegramMessage message, boolean disablePreview) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Configure TELEGRAM_BOT_TOKEN para responder às mensagens.");
        }
        for (var part : message.parts()) {
            sendPart(chatId, replyToMessageId, part, disablePreview);
        }
    }

    private void sendPart(long chatId, long messageId, TelegramMessage.Part part, boolean disablePreview) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", part.text());
        if (!part.entities().isEmpty()) body.put("entities", part.entities());
        body.put("reply_parameters", Map.of("message_id", messageId, "allow_sending_without_reply", true));
        if (disablePreview) body.put("link_preview_options", Map.of("is_disabled", true));
        try {
            var response = client.post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve().body(SendMessageResponse.class);
            if (response == null || !Boolean.TRUE.equals(response.ok())) {
                throw new IllegalStateException("Telegram não confirmou o envio da resposta.");
            }
        } catch (RestClientException failure) {
            // Não propaga URL, token ou conteúdo da resposta de erro da API.
            throw new IllegalStateException("Falha ao enviar resposta ao Telegram.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendMessageResponse(Boolean ok) {}
}
