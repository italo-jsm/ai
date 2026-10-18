package com.italo.ai.api;

import com.italo.ai.memory.model.MemoryExtraction;
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
        when(context.contextFor("alice")).thenReturn("Memórias: prefere café sem açúcar");
        var extractor = mock(MemoryExtractor.class);
        var extraction = new MemoryExtraction(List.of());
        when(extractor.extract("Sugira uma bebida")).thenReturn(extraction);
        var memoryService = mock(MemoryService.class);
        var controller = new ChatController(client, extractor, memoryService, context);
        assertThat(controller.chat(new ChatController.ChatRequest("alice", "Sugira uma bebida")).response()).isEqualTo("Resposta");
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions()).extracting(message -> message.getText())
            .contains("Responda em português brasileiro.", "Memórias: prefere café sem açúcar", "Sugira uma bebida");
        verify(memoryService).save("alice", extraction);
    }
}
