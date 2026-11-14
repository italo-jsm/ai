package com.italo.ai.user;

import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:users;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserService.class)
class UserPersistenceTest {
    @Autowired UserService users;
    @Autowired UserRepository repository;
    @Autowired MemoryRepository memories;

    @Test void normalizesUsernameAndResolvesSameUserThroughBothChannels() {
        var created = users.create("  ItAlO  ", 5123456789L);
        assertThat(created.getUsername()).isEqualTo("italo");
        assertThat(users.findByUsername(" ITALO ").getId()).isEqualTo(created.getId());
        assertThat(users.findByTelegramUserId(5123456789L)).get()
                .extracting(UserEntity::getId).isEqualTo(created.getId());
        var memory = memories.saveAndFlush(new MemoryEntity(created,
                new MemoryCandidate(MemoryType.PREFERENCE, "usuário", "prefere", "café", null)));
        assertThat(memory.getUserId()).isEqualTo(created.getId());
    }

    @Test void linksTelegramAfterRegistrationWithoutChangingUserOrMemories() {
        var user = users.create("italo", null);
        var memory = memories.saveAndFlush(new MemoryEntity(user,
                new MemoryCandidate(MemoryType.FACT, "usuário", "mora em", "Recife", null)));
        var linked = users.linkTelegram("ITALO", 5123456789L);
        assertThat(linked.getId()).isEqualTo(user.getId());
        assertThat(linked.getTelegramUserId()).isEqualTo(5123456789L);
        assertThat(memories.findByIdInAndUserId(java.util.List.of(memory.getId()), linked.getId()))
                .extracting(MemoryEntity::getId).containsExactly(memory.getId());
    }

    @Test void rejectsDuplicateNormalizedUsername() {
        users.create("italo", null);
        assertThatThrownBy(() -> users.create(" ITALO ", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void rejectsDuplicateTelegramAtRegistration() {
        users.create("italo", 5123456789L);
        assertThatThrownBy(() -> users.create("outro", 5123456789L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void rejectsTelegramAlreadyLinkedToAnotherUser() {
        users.create("italo", 5123456789L);
        users.create("outro", null);
        assertThatThrownBy(() -> users.linkTelegram("outro", 5123456789L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void allowsMultipleUsersWithoutTelegram() {
        users.create("italo", null);
        users.create("outro", null);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test void rejectsInvalidInputAndUnknownUsername() {
        assertThatThrownBy(() -> users.create(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.create(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.create("x".repeat(256), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.create("italo", 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.linkTelegram("italo", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> users.findByUsername("ausente")).isInstanceOf(UserNotFoundException.class);
        assertThat(users.findByTelegramUserId(123L)).isEmpty();
    }
}
