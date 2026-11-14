package com.italo.ai.memory.service;

import com.italo.ai.memory.model.MemoryCandidate;
import com.italo.ai.memory.model.MemoryExtraction;
import com.italo.ai.memory.model.MemoryType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryExtractorTest {

    @Test
    void extractsStructuredMemoriesAndSendsUserMessageWithDeterministicOptions() {
        var model = modelReturning("""
                {"memories":[{"type":"PREFERENCE","subject":"usuário",
                "predicate":"gosta de","value":"café","reason":"Gosto explícito por café."}]}
                """);
        var extractor = new MemoryExtractor(ChatClient.builder(model));

        MemoryExtraction result = extractor.extract("Eu gosto de café.");

        assertThat(result.memories()).containsExactly(new MemoryCandidate(
                MemoryType.PREFERENCE, "usuário", "gosta de", "café", "Gosto explícito por café."));
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getUserMessage().getText()).startsWith("Eu gosto de café.");
        assertThat(prompt.getValue().getSystemMessage().getText())
                .contains("Extraia somente fatos pessoais explicitamente afirmados pelo usuário");
        assertThat(prompt.getValue().getOptions().getTemperature()).isEqualTo(0.0);
    }

    @Test
    void returnsEmptyMemoriesWhenModelFindsNoPersonalFacts() {
        var extractor = new MemoryExtractor(ChatClient.builder(modelReturning("{\"memories\":[]}")));

        assertThat(extractor.extract("Que horas são?").memories()).isEmpty();
    }

    private ChatModel modelReturning(String content) {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
        return model;
    }
}
