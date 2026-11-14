package com.italo.ai.chat;

import com.italo.ai.memory.model.MemoryExtraction;
import com.italo.ai.user.UserEntity;
import com.italo.ai.memory.service.MemoryExtractor;
import com.italo.ai.memory.service.MemoryService;
import com.italo.ai.memory.service.MemoryContextService;
import org.springframework.ai.chat.messages.SystemMessage;
import com.italo.ai.memory.indexing.AsyncMemoryIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final MemoryExtractor extractor;
    private final MemoryService memoryService;
    private final MemoryContextService memoryContext;
    private final AsyncMemoryIndexingService memoryIndexer;
    private static final Logger log =
            LoggerFactory.getLogger(ChatService.class);

    public ChatService(ChatClient chatClient, MemoryExtractor extractor, MemoryService memoryService, MemoryContextService memoryContext, AsyncMemoryIndexingService memoryIndexer) {
        this.chatClient = chatClient;
        this.extractor = extractor;
        this.memoryService = memoryService;
        this.memoryContext = memoryContext;
        this.memoryIndexer = memoryIndexer;
    }

    public String chat(UserEntity user, String message) {
        if (user == null) throw new IllegalArgumentException("Informe um usuário cadastrado.");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Informe uma mensagem.");
        String context = memoryContext.contextFor(user.getId(), message);
        var prompt = chatClient.prompt();
        if (!context.isEmpty()) prompt.messages(new SystemMessage(context));
        String answer = prompt
                .user(message)
                .call()
                .content();
        MemoryExtraction extraction =
                extractor.extract(message);
        var saved = memoryService.save(user, extraction);
        var memories = saved.stream().map(MemoryService.MemoryView::from).toList();
        try {
            memoryIndexer.index(memories);
        } catch (RuntimeException failure) {
            log.warn("Falha ao agendar indexação de {} memórias; tipo={}", memories.size(), failure.getClass().getSimpleName());
        }
        return answer;
    }

}
