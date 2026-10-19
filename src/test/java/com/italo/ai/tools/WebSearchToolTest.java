package com.italo.ai.tools;

import com.italo.ai.config.AiConfiguration;
import com.italo.ai.search.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSearchToolTest {
    @Test void exposesOnlyQueryAndReturnsResultsThroughRealToolCallback() {
        var search = mock(BraveSearchService.class);
        when(search.search("Fernando da Gata")).thenReturn(List.of(
            new SearchResult("Biografia", "https://example.com/biografia", "Trecho encontrado")));
        var callback = ToolCallbacks.from(new WebSearchTool(search))[0];
        var schema = JsonMapper.builder().build().readTree(callback.getToolDefinition().inputSchema());
        assertThat(schema.path("properties").size()).isEqualTo(1);
        assertThat(schema.path("properties").has("query")).isTrue();
        var result = JsonMapper.builder().build().readTree(callback.call("{\"query\":\"Fernando da Gata\"}"));
        assertThat(result.path("results").size()).isEqualTo(1);
        assertThat(result.path("results").get(0).path("url").asString()).isEqualTo("https://example.com/biografia");
        assertThat(result.path("results").get(0).path("description").asString()).isEqualTo("Trecho encontrado");
    }

    @Test void failedSearchReturnsExplicitErrorInsteadOfPretendingNoResults() {
        var search = mock(BraveSearchService.class);
        when(search.search("consulta")).thenThrow(new BraveSearchException(BraveSearchException.Reason.TIMEOUT));
        var callback = ToolCallbacks.from(new WebSearchTool(search))[0];
        var result = JsonMapper.builder().build().readTree(callback.call("{\"query\":\"consulta\"}"));
        assertThat(result.path("results").size()).isZero();
        assertThat(result.path("error").asString()).contains("TIMEOUT");
    }

    @Test void invalidQueryReturnsActionableError() {
        var search = mock(BraveSearchService.class);
        when(search.search(" ")).thenThrow(new IllegalArgumentException("consulta inválida"));
        var result = new WebSearchTool(search).searchWeb(" ");
        assertThat(result.results()).isEmpty();
        assertThat(result.error()).contains("INVALID_QUERY");
    }

    @Test void successfulEmptySearchIsDistinctFromFailure() {
        var search = mock(BraveSearchService.class);
        when(search.search("consulta")).thenReturn(List.of());
        var result = new WebSearchTool(search).searchWeb("consulta");
        assertThat(result.results()).isEmpty();
        assertThat(result.error()).isNull();
    }

    @Test void chatClientExposesWebSearchAlongsideTimeToolToModel() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
            new Generation(new AssistantMessage("Resposta")))));
        var search = mock(BraveSearchService.class);
        when(search.search("Fernando da Gata")).thenReturn(List.of(
            new SearchResult("Biografia", "https://example.com", "Fonte")));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ChatClient.Builder.class, () -> ChatClient.builder(model));
            context.registerBean(BraveSearchService.class, () -> search);
            context.register(AiConfiguration.class, TimeTool.class, WebSearchTool.class);
            context.refresh();
            var client = context.getBean(ChatClient.class);
            assertThat(client.prompt().user("Quem foi Fernando da Gata?").call().content()).isEqualTo("Resposta");
            var prompt = ArgumentCaptor.forClass(Prompt.class);
            verify(model).call(prompt.capture());
            var options = (ToolCallingChatOptions) prompt.getValue().getOptions();
            assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .contains("searchWeb", "getLocalDateTime");
            var web = options.getToolCallbacks().stream()
                .filter(callback -> callback.getToolDefinition().name().equals("searchWeb")).findFirst().orElseThrow();
            assertThat(web.call("{\"query\":\"Fernando da Gata\"}")).contains("https://example.com");
        }
    }
}
