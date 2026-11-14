package com.italo.ai.config;

import com.italo.ai.search.BraveSearchService;
import com.italo.ai.tools.TimeTool;
import com.italo.ai.tools.WebSearchTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConfigurationTest {

    @Test
    void createsChatClientWithReasoningFilterEnabledOrDisabled() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        var contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(AiConfiguration.class)
                .withBean(ChatClient.Builder.class, () -> ChatClient.builder(model))
                .withBean(TimeTool.class, TimeTool::new)
                .withBean(WebSearchTool.class, () -> new WebSearchTool(mock(BraveSearchService.class)));

        for (boolean enabled : new boolean[]{false, true}) {
            contextRunner.withPropertyValues("app.groq.reasoning-history-filter-enabled=" + enabled)
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(ChatClient.class);
                        assertThat(context.getBean(ChatClient.class)).isNotNull();
                    });
        }
    }
}
