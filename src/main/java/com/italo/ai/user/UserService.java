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
    public UserEntity create(String username, Long telegramUserId, String password) {
        var user = new UserEntity(username, telegramUserId);
        user.setPasswordHash(Passwords.hash(password));
        return users.saveAndFlush(user);
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
    public Optional<UserEntity> linkTelegram(String username, Long telegramUserId, String password) {
        if (telegramUserId == null || telegramUserId <= 0)
            throw new IllegalArgumentException("Informe um ID positivo do Telegram.");
        var found = users.findForUpdate(UserEntity.normalizeUsername(username));
        if (found.isEmpty()) {
            Passwords.matches(password, null);
            return Optional.empty();
        }
        var user = found.get();
        if (user.isLoginLocked()) return Optional.empty();
        if (!Passwords.matches(password, user.getPasswordHash())) {
            user.recordLoginFailure();
            return Optional.empty();
        }
        if (user.getTelegramUserId() != null && !user.getTelegramUserId().equals(telegramUserId))
            return Optional.empty();
        var linked = users.findByTelegramUserId(telegramUserId);
        if (linked.isPresent() && !linked.get().getId().equals(user.getId())) return Optional.empty();
        user.clearLoginFailures();
        user.setTelegramUserId(telegramUserId);
        return Optional.of(users.saveAndFlush(user));
    }
}
