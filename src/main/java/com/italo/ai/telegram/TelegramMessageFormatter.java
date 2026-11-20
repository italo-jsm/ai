package com.italo.ai.telegram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

final class TelegramMessageFormatter {
    private static final Pattern INLINE = Pattern.compile(
            "`[^`\\r\\n]+`|\\*\\*([^*\\r\\n]+)\\*\\*|(?<![\\p{L}\\p{N}_])__([^_\\r\\n]+)__(?![\\p{L}\\p{N}_])");
    private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})");
    private static final Pattern SEPARATOR = Pattern.compile(":?-{3,}:?");

    private TelegramMessageFormatter() {}

    static TelegramMessage format(String markdown) {
        TelegramMessage.validateText(markdown);
        String[] lines = markdown.split("\\R", -1);
        StringBuilder text = new StringBuilder();
        List<TelegramMessage.Bold> bold = new ArrayList<>();
        String fence = "";
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) text.append('\n');
            String line = lines[i];
            String trimmed = line.stripLeading();
            if (!fence.isEmpty()) {
                text.append(line);
                if (trimmed.startsWith(fence)
                        && trimmed.stripTrailing().chars().allMatch(character -> character == trimmed.charAt(0))) {
                    fence = "";
                }
                continue;
            }
            var openingFence = FENCE.matcher(trimmed);
            if (openingFence.find()) {
                fence = openingFence.group();
                text.append(line);
                continue;
            }
            List<String> headers = tableHeaders(lines, i);
            if (headers.isEmpty()) {
                appendInline(line, text, bold);
                continue;
            }
            i += 2;
            appendRow(headers, cells(lines[i]), text, bold);
            while (i + 1 < lines.length) {
                List<String> row = cells(lines[i + 1]);
                if (row.size() != headers.size()) break;
                text.append("\n\n");
                appendRow(headers, row, text, bold);
                i++;
            }
        }
        return new TelegramMessage(text.toString(), bold);
    }

    private static List<String> tableHeaders(String[] lines, int index) {
        if (index + 2 >= lines.length) return List.of();
        List<String> headers = cells(lines[index]);
        if (headers.size() < 2) return List.of();
        List<String> separator = cells(lines[index + 1]);
        if (separator.size() != headers.size() || cells(lines[index + 2]).size() != headers.size()
                || !separator.stream().allMatch(cell -> SEPARATOR.matcher(cell).matches())) {
            return List.of();
        }
        return headers;
    }

    private static List<String> cells(String line) {
        String row = line.strip();
        if (row.startsWith("|")) row = row.substring(1);
        if (row.endsWith("|") && !row.endsWith("\\|")) row = row.substring(0, row.length() - 1);
        return Arrays.stream(row.split("(?<!\\\\)\\|", -1))
                .map(cell -> cell.strip().replace("\\|", "|"))
                .toList();
    }

    private static void appendRow(List<String> headers, List<String> row, StringBuilder text, List<TelegramMessage.Bold> bold) {
        text.append("• ");
        appendInline(row.getFirst(), text, bold, true);
        for (int column = 1; column < row.size(); column++) {
            if (row.get(column).isBlank()) continue;
            text.append("\n  ");
            appendInline(headers.get(column), text, bold);
            text.append(": ");
            appendInline(row.get(column), text, bold);
        }
    }

    private static void appendInline(String line, StringBuilder text, List<TelegramMessage.Bold> bold) {
        appendInline(line, text, bold, false);
    }

    private static void appendInline(String line, StringBuilder text, List<TelegramMessage.Bold> bold,
                                     boolean boldEntireLine) {
        int lineStart = text.length();
        var matcher = INLINE.matcher(line);
        int previousEnd = 0;
        while (matcher.find()) {
            text.append(line, previousEnd, matcher.start());
            String emphasis = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (emphasis == null || emphasis.isBlank()) {
                text.append(matcher.group());
            } else {
                int start = text.length();
                text.append(emphasis);
                if (!boldEntireLine) bold.add(new TelegramMessage.Bold(start, text.length()));
            }
            previousEnd = matcher.end();
        }
        text.append(line, previousEnd, line.length());
        if (boldEntireLine && text.length() > lineStart) {
            bold.add(new TelegramMessage.Bold(lineStart, text.length()));
        }
    }
}
