package com.italo.ai.api;

import com.italo.ai.chat.ChatService;
import com.italo.ai.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    private final UserService users;
    private final boolean requireAuthentication;
    private final BasicAuthenticationConverter authentication = new BasicAuthenticationConverter();

    public ChatController(ChatService chatService, UserService users,
                          @Value("${app.chat.require-authentication:false}") boolean requireAuthentication) {
        this.chatService = chatService;
        this.users = users;
        this.requireAuthentication = requireAuthentication;
    }

    public ChatResponse chat(ChatRequest request) {
        return new ChatResponse(chatService.chat(users.findByUsername(request.username()), request.message()));
    }

    @PostMapping
    public ResponseEntity<ChatResponse> handleChat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        if (!requireAuthentication) {
            return ResponseEntity.ok(chat(request));
        }

        try {
            var credentials = authentication.convert(httpRequest);
            if (credentials != null) {
                var user = users.authenticate(credentials.getName(), (String) credentials.getCredentials());
                if (user.isPresent()) {
                    return ResponseEntity.ok(new ChatResponse(chatService.chat(user.get(), request.message())));
                }
            }
        } catch (BadCredentialsException ignored) {
            // Malformed Basic credentials are handled with the same challenge as missing credentials.
        }
        return ResponseEntity.status(401)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"AI\", charset=\"UTF-8\"")
                .build();
    }

    public record ChatRequest(String username, String message) {}
    public record ChatResponse(String response) {}
}
