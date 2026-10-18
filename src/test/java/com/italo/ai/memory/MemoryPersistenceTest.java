package com.italo.ai.memory;

import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase(replace = org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {"spring.datasource.url=jdbc:h2:mem:memories;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=validate", "spring.liquibase.enabled=true"})
class MemoryPersistenceTest {
    @Autowired MemoryRepository memories;

    @Test void savesMemoriesAndSearchesOnlyOwnerMemories() {
        var candidate = new MemoryCandidate(MemoryType.PREFERENCE, "usuário", "prefere", "café", "preferência explícita");
        var saved = memories.saveAndFlush(new MemoryEntity("alice", candidate));
        memories.saveAndFlush(new MemoryEntity("bob", candidate));
        var page = memories.search("alice", MemoryType.PREFERENCE, "usuário", "prefere", "CAFÉ", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getId()).isEqualTo(saved.getId());
        assertThat(page.getContent().getFirst().getUserId()).isEqualTo("alice");
        assertThat(page.getContent().getFirst().getCreatedAt()).isNotNull();
        assertThat(page.getContent().getFirst().getValue()).isEqualTo("café");
        assertThat(memories.search("unknown", null, null, null, null, PageRequest.of(0, 10))).isEmpty();
    }
    @Test void limitsContextToMostRecentMemoriesOfRequestedUser() {
        var candidate = new MemoryCandidate(MemoryType.FACT, "alice", "vive em", "Recife", null);
        var old = memories.saveAndFlush(new MemoryEntity("alice", candidate));
        var recent = memories.saveAndFlush(new MemoryEntity("alice", candidate));
        memories.saveAndFlush(new MemoryEntity("bob", candidate));
        var page = memories.search("alice", null, null, null, null, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(MemoryEntity::getId).containsExactly(recent.getId());
        assertThat(page.getContent()).extracting(MemoryEntity::getUserId).containsOnly("alice");
    }
}
