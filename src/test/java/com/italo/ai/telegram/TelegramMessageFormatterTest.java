package com.italo.ai.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageFormatterTest {
    @Test void preservesMarkdownInsideCodeFences() {
        String code = """
                ````markdown
                ```
                | Nome | Tipo |
                | --- | --- |
                | **Itaú** | Conta corrente |
                `````
                """;

        var message = TelegramMessageFormatter.format(code + "**Fim**");

        assertThat(message.text()).isEqualTo(code + "Fim");
        assertThat(message.bold()).containsExactly(new TelegramMessage.Bold(code.length(), code.length() + 3));
    }
}
