package com.italo.ai.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class TelegramBotClient {
    private static final int TEXT_LIMIT = 4096;
    private final RestClient client;
    private final String token;

    public TelegramBotClient(RestClient client, String token) {
        this.client = client;
        this.token = token;
    }

    public void sendMessage(long chatId, long replyToMessageId, String text) {
        sendMessage(chatId, replyToMessageId, text, false);
    }

    public void sendMessageWithoutPreview(long chatId, long replyToMessageId, String text) {
        sendMessage(chatId, replyToMessageId, text, true);
    }

    private void sendMessage(long chatId, long replyToMessageId, String text, boolean disablePreview) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Configure TELEGRAM_BOT_TOKEN para responder às mensagens.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A resposta do chat está vazia.");
        }
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + TEXT_LIMIT, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            sendPart(chatId, replyToMessageId, text.substring(start, end), disablePreview);
            start = end;
        }
    }

    private void sendPart(long chatId, long messageId, String text, boolean disablePreview) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
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
