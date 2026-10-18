package com.italo.ai.memory;

import com.italo.ai.memory.service.MemoryContextService;
import com.italo.ai.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryContextServiceTest {
    @Test void buildsContextFromLimitedOwnerMemories() {
        var memories = mock(MemoryService.class);
        var memory = new MemoryService.MemoryView(UUID.randomUUID(), "alice", Instant.now(),
            com.italo.ai.memory.model.MemoryType.PREFERENCE, "alice", "prefere", "café sem açúcar", "explícito");
        when(memories.search("alice", null, null, null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(memory)));
        var context = new MemoryContextService(memories, 20).contextFor("alice");
        assertThat(context).contains("café sem açúcar", "PREFERENCE", "alice", "prefere");
        assertThat(context).doesNotContain(memory.id().toString(), "explícito");
    }
    @Test void returnsNoContextWhenUserHasNoMemories() {
        var memories = mock(MemoryService.class);
        when(memories.search("bob", null, null, null, null, PageRequest.of(0, 20))).thenReturn(Page.empty());
        assertThat(new MemoryContextService(memories, 20).contextFor("bob")).isEmpty();
    }
    @Test void rejectsInvalidLimit() {
        assertThatThrownBy(() -> new MemoryContextService(mock(MemoryService.class), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void translatesLegacyDislikeIntoExplicitNegativeStatement() {
        var memories = mock(MemoryService.class);
        var memory = new MemoryService.MemoryView(UUID.randomUUID(), "italo", Instant.now(),
            com.italo.ai.memory.model.MemoryType.PREFERENCE, "petista", "DISLIKE", "true", "political_affiliation");
        when(memories.search("italo", null, null, null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(memory)));
        assertThat(new MemoryContextService(memories, 20).contextFor("italo"))
            .contains("O usuário não gosta de petista.");
    }
    @Test void preservesExplicitNegationInNewMemories() {
        var memories = mock(MemoryService.class);
        var memory = new MemoryService.MemoryView(UUID.randomUUID(), "italo", Instant.now(),
            com.italo.ai.memory.model.MemoryType.PREFERENCE, "usuário", "não gosta de", "café", null);
        when(memories.search("italo", null, null, null, null, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(memory)));
        assertThat(new MemoryContextService(memories, 20).contextFor("italo"))
            .contains("predicado: não gosta de; valor: café");
    }
}
