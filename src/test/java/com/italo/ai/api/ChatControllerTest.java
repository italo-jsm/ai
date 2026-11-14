package com.italo.ai.api;

import com.italo.ai.memory.service.MemoryExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    MemoryExtractor extractor;

    @MockBean
    org.springframework.ai.chat.client.ChatClient chatClient;

    @Test
    @DisplayName("chat returns response and calls extractor")
    void chatShouldReturnAnswerAndExtractMemory() throws Exception {
        // Arrange
        String answer = "Oi!";
        when(chatClient.prompt()).thenReturn(Mockito.mock(org.springframework.ai.chat.client.PromptRequest.class));
        // For simplicity, allow any call chain to return a PromptResponse that yields answer
        when(chatClient.prompt().user(anyString()).call()).thenAnswer(invocation -> {
            org.springframework.ai.chat.client.PromptResponse response = Mockito.mock(org.springframework.ai.chat.client.PromptResponse.class);
            when(response.content()).thenReturn(answer);
            return response;
        });
        when(extractor.extract(anyString())).thenReturn(new com.italo.ai.memory.model.MemoryExtraction(java.util.List.of()));

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(new ChatController.ChatRequest("olá"));

        // Act & Assert
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(containsString(answer)));

        Mockito.verify(extractor).extract(anyString());
    }
}
