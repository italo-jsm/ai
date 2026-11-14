package com.italo.ai.telegram.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Payload publicado na fila pelo webhook do Telegram. IDs exigem 64 bits. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") Long updateId,
        Message message,
        @JsonProperty("edited_message") Message editedMessage,
        @JsonProperty("channel_post") Message channelPost,
        @JsonProperty("edited_channel_post") Message editedChannelPost) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") Long messageId,
            User from,
            Chat chat,
            Long date,
            String text,
            String caption) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(
            Long id,
            @JsonProperty("is_bot") Boolean bot,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String username,
            @JsonProperty("language_code") String languageCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(
            Long id,
            String type,
            String title,
            String username,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName) {}
}
