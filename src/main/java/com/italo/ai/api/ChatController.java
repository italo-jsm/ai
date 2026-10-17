package com.italo.ai.api;

import com.italo.ai.memory.model.MemoryExtraction;
import com.italo.ai.memory.service.MemoryExtractor;
import com.italo.ai.tools.TestTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final MemoryExtractor extractor;
    private static final Logger log =
            LoggerFactory.getLogger(TestTools.class);

    public ChatController(ChatClient chatClient, MemoryExtractor extractor) {
        this.chatClient = chatClient;
        this.extractor = extractor;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {

        String message = request.message();
        log.info("Mensagem: " + message);
        String answer = chatClient.prompt()
                .user(message)
                .call()
                .content();
        log.info("Terminei de processar a mensagem");
        MemoryExtraction extraction =
                extractor.extract(message);
        log.info("Terminei de extrair as memorias");

        return new ChatResponse(answer);
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String response) {}
}
