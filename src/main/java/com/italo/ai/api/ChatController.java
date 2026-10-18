package com.italo.ai.api;

import com.italo.ai.memory.model.MemoryExtraction;
import com.italo.ai.memory.service.MemoryExtractor;
import com.italo.ai.memory.service.MemoryService;
import com.italo.ai.memory.service.MemoryContextService;
import org.springframework.ai.chat.messages.SystemMessage;
import com.italo.ai.tools.TimeTool;
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
    private static final Logger log =
            LoggerFactory.getLogger(TimeTool.class);

    public ChatController(ChatClient chatClient, MemoryExtractor extractor, MemoryService memoryService, MemoryContextService memoryContext) {
        this.chatClient = chatClient;
        this.extractor = extractor;
        this.memoryService = memoryService;
        this.memoryContext = memoryContext;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String message = request.message();
        log.info("Mensagem: " + message);
        String context = memoryContext.contextFor(request.userId());
        var prompt = chatClient.prompt();
        if (!context.isEmpty()) prompt.messages(new SystemMessage(context));
        String answer = prompt
                .user(message)
                .call()
                .content();
        MemoryExtraction extraction =
                extractor.extract(message);
        memoryService.save(request.userId(), extraction);
        return new ChatResponse(answer);
    }

    public record ChatRequest(String userId, String message) {}
    public record ChatResponse(String response) {}
}
