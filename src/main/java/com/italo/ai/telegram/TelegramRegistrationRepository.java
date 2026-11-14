package com.italo.ai.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TelegramRegistrationRepository extends JpaRepository<TelegramRegistration, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TelegramRegistration r where r.telegramUserId = :id")
    Optional<TelegramRegistration> findForUpdate(Long id);
}
