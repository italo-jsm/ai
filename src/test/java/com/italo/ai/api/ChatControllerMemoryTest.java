package com.italo.ai.api;

import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.MemoryEntity;
import com.italo.ai.memory.indexing.AsyncMemoryIndexingService;
import com.italo.ai.memory.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatControllerMemoryTest {
    @Test void includesSavedMemoriesAndPreservesDefaultSystemInstructions() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Resposta")))));
        var client = ChatClient.builder(model).defaultSystem("Responda em português brasileiro.").build();
        var context = mock(MemoryContextService.class);
        when(context.contextFor("alice", "Sugira uma bebida")).thenReturn("Memórias: prefere café sem açúcar");
        var extractor = mock(MemoryExtractor.class);
        var extraction = new MemoryExtraction(List.of());
        when(extractor.extract("Sugira uma bebida")).thenReturn(extraction);
        var memoryService = mock(MemoryService.class);
        var controller = new ChatController(client, extractor, memoryService, context, mock(AsyncMemoryIndexingService.class));
        assertThat(controller.chat(new ChatController.ChatRequest("alice", "Sugira uma bebida")).response()).isEqualTo("Resposta");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions()).extracting(message -> message.getText())
            .contains("Responda em português brasileiro.", "Memórias: prefere café sem açúcar", "Sugira uma bebida");
        verify(memoryService).save("alice", extraction);
    }

    @Test void indexesOnlyAfterSaveAndReturnsAnswer() {
        var fixture = new Fixture();
        var saved = new MemoryEntity("alice", new MemoryCandidate(MemoryType.PREFERENCE, "usuário", "prefere", "café", null));
        when(fixture.memories.save("alice", fixture.extraction)).thenReturn(List.of(saved));
        assertThat(fixture.controller.chat(new ChatController.ChatRequest("alice", "mensagem")).response()).isEqualTo("Resposta");
        var order = inOrder(fixture.memories, fixture.indexer);
        order.verify(fixture.memories).save("alice", fixture.extraction);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MemoryService.MemoryView>> indexed = ArgumentCaptor.forClass(List.class);
        order.verify(fixture.indexer).index(indexed.capture());
        assertThat(indexed.getValue()).singleElement().satisfies(memory -> {
            assertThat(memory.id()).isEqualTo(saved.getId());
            assertThat(memory.value()).isEqualTo("café");
        });
    }
    @Test void submissionFailureStillReturnsGeneratedAnswer() {
        var fixture = new Fixture();
        doThrow(new IllegalStateException("offline")).when(fixture.indexer).index(anyList());
        assertThat(fixture.controller.chat(new ChatController.ChatRequest("alice", "mensagem")).response()).isEqualTo("Resposta");
        verify(fixture.indexer, times(1)).index(anyList());
    }
    @Test void saveFailureDoesNotIndexOrHideError() {
        var fixture = new Fixture();
        when(fixture.memories.save("alice", fixture.extraction)).thenThrow(new IllegalStateException("save failed"));
        assertThatThrownBy(() -> fixture.controller.chat(new ChatController.ChatRequest("alice", "mensagem")))
            .isInstanceOf(IllegalStateException.class).hasMessage("save failed");
        verifyNoInteractions(fixture.indexer);
    }
    private static class Fixture {
        final MemoryService memories = mock(MemoryService.class);
        final AsyncMemoryIndexingService indexer = mock(AsyncMemoryIndexingService.class);
        final MemoryExtraction extraction = new MemoryExtraction(List.of());
        final ChatController controller;
        Fixture() {
            var model = mock(ChatModel.class);
            when(model.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
            when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Resposta")))));
            var context = mock(MemoryContextService.class);
            when(context.contextFor("alice", "mensagem")).thenReturn("");
            var extractor = mock(MemoryExtractor.class);
            when(extractor.extract("mensagem")).thenReturn(extraction);
            controller = new ChatController(ChatClient.builder(model).build(), extractor, memories, context, indexer);
        }
    }
}
