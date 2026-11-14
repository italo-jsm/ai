package com.italo.ai.api;

import com.italo.ai.chat.ChatService;
import com.italo.ai.user.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    private final UserService users;

    public ChatController(ChatService chatService, UserService users) {
        this.chatService = chatService;
        this.users = users;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chat(users.findByUsername(request.username()), request.message()));
    }

    public record ChatRequest(String username, String message) {}
    public record ChatResponse(String response) {}
}
