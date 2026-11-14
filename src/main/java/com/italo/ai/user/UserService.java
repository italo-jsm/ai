package com.italo.ai.user;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public UserEntity create(String username, Long telegramUserId) {
        return users.saveAndFlush(new UserEntity(username, telegramUserId));
    }

    @Transactional(readOnly = true)
    public UserEntity findByUsername(String username) {
        return users.findByUsername(UserEntity.normalizeUsername(username))
                .orElseThrow(UserNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findByTelegramUserId(long telegramUserId) {
        return users.findByTelegramUserId(telegramUserId);
    }

    @Transactional
    public UserEntity linkTelegram(String username, Long telegramUserId) {
        if (telegramUserId == null)
            throw new IllegalArgumentException("Informe o ID do usuário do Telegram.");
        var user = findByUsername(username);
        user.setTelegramUserId(telegramUserId);
        return users.saveAndFlush(user);
    }
}
