package com.italo.ai.api;

import com.italo.ai.user.UserEntity;
import com.italo.ai.user.UserService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
        var user = users.create(request.username(), request.telegramUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PutMapping("/{username}/telegram")
    public UserResponse linkTelegram(@PathVariable String username, @RequestBody TelegramLinkRequest request) {
        return UserResponse.from(users.linkTelegram(username, request.telegramUserId()));
    }

    public record CreateUserRequest(String username, Long telegramUserId) {}
    public record TelegramLinkRequest(Long telegramUserId) {}
    public record UserResponse(UUID id, String username, Long telegramUserId) {
        static UserResponse from(UserEntity user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getTelegramUserId());
        }
    }
}
