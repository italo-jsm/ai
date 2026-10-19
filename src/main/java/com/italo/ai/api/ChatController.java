package com.italo.ai.api;

import com.italo.ai.memory.model.MemoryExtraction;
import com.italo.ai.memory.service.MemoryExtractor;
import com.italo.ai.memory.service.MemoryService;
import com.italo.ai.memory.service.MemoryContextService;
import org.springframework.ai.chat.messages.SystemMessage;
import com.italo.ai.memory.indexing.AsyncMemoryIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final MemoryExtractor extractor;
    private final MemoryService memoryService;
    private final MemoryContextService memoryContext;
    private final AsyncMemoryIndexingService memoryIndexer;
    private static final Logger log =
            LoggerFactory.getLogger(ChatController.class);

    public ChatController(ChatClient chatClient, MemoryExtractor extractor, MemoryService memoryService, MemoryContextService memoryContext, AsyncMemoryIndexingService memoryIndexer) {
        this.chatClient = chatClient;
        this.extractor = extractor;
        this.memoryService = memoryService;
        this.memoryContext = memoryContext;
        this.memoryIndexer = memoryIndexer;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String message = request.message();
        String context = memoryContext.contextFor(request.userId(), message);
        var prompt = chatClient.prompt();
        if (!context.isEmpty()) prompt.messages(new SystemMessage(context));
        String answer = prompt
                .user(message)
                .call()
                .content();
        MemoryExtraction extraction =
                extractor.extract(message);
        var saved = memoryService.save(request.userId(), extraction);
        var memories = saved.stream().map(MemoryService.MemoryView::from).toList();
        try {
            memoryIndexer.index(memories);
        } catch (RuntimeException failure) {
            // SQL has committed; executor rejection must not discard the generated answer.
            log.warn("Falha ao agendar indexação de {} memórias; tipo={}", memories.size(), failure.getClass().getSimpleName());
        }
        return new ChatResponse(answer);
    }

    public record ChatRequest(String userId, String message) {}
    public record ChatResponse(String response) {}
}
