package com.italo.ai.user;

import com.italo.ai.api.ChatController;
import com.italo.ai.chat.ChatService;
import com.italo.ai.memory.indexing.AsyncMemoryIndexingService;
import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import com.italo.ai.memory.retrieval.*;
import com.italo.ai.memory.service.*;
import com.italo.ai.telegram.*;
import com.italo.ai.telegram.model.TelegramUpdate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user-chat;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.telegram.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserService.class, ChatController.class, ChatService.class, TelegramConsumer.class,
        MemoryService.class, MemoryContextService.class, MemoryTextFormatter.class,
        UserChatIntegrationTest.ChatTestConfiguration.class})
class UserChatIntegrationTest {
    @Autowired UserService users;
    @Autowired ChatController http;
    @Autowired TelegramConsumer telegram;
    @Autowired MemoryRepository memories;
    @MockitoBean ChatModel model;
    @MockitoBean MemoryExtractor extractor;
    @MockitoBean TelegramBotClient bot;
    @MockitoBean AsyncMemoryIndexingService indexer;
    @MockitoBean SemanticMemoryRetriever semantic;

    @Test void httpAndTelegramShareRegisteredUsersMemoriesWithoutMixingOwners() {
        var user = users.create("italo", 5123456789L);
        var other = users.create("outro", null);
        memories.saveAndFlush(new MemoryEntity(other,
                new MemoryCandidate(MemoryType.PREFERENCE, "usuário", "prefere", "chá secreto", null)));
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Resposta")))));
        when(extractor.extract("Prefiro café")).thenReturn(new MemoryExtraction(List.of(
                new MemoryCandidate(MemoryType.PREFERENCE, "usuário", "prefere", "café", null))));
        when(extractor.extract("Qual minha bebida preferida?")).thenReturn(new MemoryExtraction(List.of()));

        assertThat(http.chat(new ChatController.ChatRequest("ITALO", "Prefiro café")).response()).isEqualTo("Resposta");
        var sender = new TelegramUpdate.User(5123456789L, false, null, null, null, null);
        var chat = new TelegramUpdate.Chat(9876543210L, "private", null, null, null, null);
        telegram.consume(new TelegramUpdate(42L, new TelegramUpdate.Message(7L, sender, chat, null,
                "Qual minha bebida preferida?", null), null, null, null));

        var prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompts.capture());
        String telegramPrompt = prompts.getAllValues().getLast().getContents();
        assertThat(telegramPrompt).contains("café").doesNotContain("chá secreto");
        assertThat(memories.findAll()).filteredOn(memory -> memory.getUserId().equals(user.getId()))
                .singleElement().satisfies(memory -> assertThat(memory.getValue()).isEqualTo("café"));
        verify(bot).sendMessage(9876543210L, 7L, "Resposta");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ChatTestConfiguration {
        @Bean ChatClient chatClient(ChatModel model) {
            when(model.getOptions()).thenReturn(ChatOptions.builder().build());
            return ChatClient.builder(model).build();
        }
        @Bean @Primary MemoryRetriever memoryRetriever(MemoryRepository memories) {
            return new SqlMemoryRetriever(memories);
        }
    }
}
