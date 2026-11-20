package com.italo.ai.telegram;

import java.util.ArrayList;
import java.util.List;

record TelegramMessage(String text, List<Bold> bold) {
    private static final int TEXT_LIMIT = 4096;

    TelegramMessage {
        validateText(text);
        bold = List.copyOf(bold);
    }

    static TelegramMessage plainText(String text) {
        return new TelegramMessage(text, List.of());
    }

    static void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("A resposta do chat está vazia.");
        }
    }

    List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        for (int start = 0; start < text.length();) {
            int end = start + Math.min(TEXT_LIMIT, text.length() - start);
            if (end < text.length() && Character.isSurrogatePair(text.charAt(end - 1), text.charAt(end))) {
                end--;
            }
            parts.add(new Part(text.substring(start, end), entitiesBetween(start, end)));
            start = end;
        }
        return parts;
    }

    private List<Entity> entitiesBetween(int start, int end) {
        List<Entity> entities = new ArrayList<>();
        for (var span : bold) {
            if (span.start() >= end) break;
            int from = Math.max(start, span.start());
            int to = Math.min(end, span.end());
            if (from < to) {
                entities.add(new Entity("bold", from - start, to - from));
            }
        }
        return entities;
    }

    record Bold(int start, int end) {}

    record Entity(String type, int offset, int length) {}

    record Part(String text, List<Entity> entities) {}
}
