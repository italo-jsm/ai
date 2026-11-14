package com.italo.ai.memory.service;

import com.italo.ai.memory.model.MemoryExtraction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.PromptRequest;
import org.springframework.ai.chat.client.PromptResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MemoryExtractorTest {

    @Test
    @DisplayName("extract returns MemoryExtraction")
    void extractShouldReturnExtraction() {
        // Arrange
        ChatClient.Builder builder = Mockito.mock(ChatClient.Builder.class);
        ChatClient chatClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(builder.clone()).thenReturn(builder); // chain for build
        when(builder.build()).thenReturn(chatClient);

        MemoryExtractor extractor = new MemoryExtractor(builder);

        // Stub deep chain: prompt().options(...).system(...).user(...).call().entity()
        PromptRequest pr = Mockito.mock(PromptRequest.class);
        PromptResponse prRes = Mockito.mock(PromptResponse.class);
        when(chatClient.prompt()).thenReturn(pr);
        when(pr.options(any()).system(any()).user(any()).call()).thenReturn(prRes);
        when(prRes.entity(MemoryExtraction.class)).thenReturn(new MemoryExtraction(java.util.List.of()));

        // Act
        MemoryExtraction result = extractor.extract("foo");

        // Assert
        assertNotNull(result);
    }
}
