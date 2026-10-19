package com.italo.ai.memory;

import com.italo.ai.memory.service.MemoryContextService;
import com.italo.ai.memory.service.MemoryService;
import com.italo.ai.memory.service.MemoryTextFormatter;
import com.italo.ai.memory.retrieval.MemoryRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryContextServiceTest {
    @Test void buildsContextFromLimitedOwnerMemories() {
        var memories = mock(MemoryRetriever.class);
        var memory = new MemoryService.MemoryView(UUID.randomUUID(), "alice", Instant.now(),
            com.italo.ai.memory.model.MemoryType.PREFERENCE, "alice", "prefere", "café sem açúcar", "explícito");
        when(memories.retrieve("alice", "bebida", 20))
            .thenReturn(List.of(memory));
        var context = new MemoryContextService(memories, new MemoryTextFormatter(), 20).contextFor("alice", "bebida");
        assertThat(context).contains("café sem açúcar", "PREFERENCE", "alice", "prefere");
        assertThat(context).doesNotContain(memory.id().toString(), "explícito");
    }
    @Test void returnsNoContextWhenUserHasNoMemories() {
        var memories = mock(MemoryRetriever.class);
        when(memories.retrieve("bob", "bebida", 20)).thenReturn(List.of());
        assertThat(new MemoryContextService(memories, new MemoryTextFormatter(), 20).contextFor("bob", "bebida")).isEmpty();
    }
    @Test void rejectsInvalidLimit() {
        assertThatThrownBy(() -> new MemoryContextService(mock(MemoryRetriever.class), new MemoryTextFormatter(), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void translatesLegacyDislikeIntoExplicitNegativeStatement() {
        var memories = mock(MemoryRetriever.class);
        var memory = new MemoryService.MemoryView(UUID.randomUUID(), "italo", Instant.now(),
            com.italo.ai.memory.model.MemoryType.PREFERENCE, "petista", "DISLIKE", "true", "political_affiliation");
        when(memories.retrieve("italo", "bebida", 20))
            .thenReturn(List.of(memory));
        assertThat(new MemoryContextService(memories, new MemoryTextFormatter(), 20).contextFor("italo", "bebida"))
            .contains("O usuário não gosta de petista.");
    }
    @Test void preservesExplicitNegationInNewMemories() {
        var memories = mock(MemoryRetriever.class);
        var memory = new MemoryService.MemoryView(UUID.randomUUID(), "italo", Instant.now(),
            com.italo.ai.memory.model.MemoryType.PREFERENCE, "usuário", "não gosta de", "café", null);
        when(memories.retrieve("italo", "bebida", 20))
            .thenReturn(List.of(memory));
        assertThat(new MemoryContextService(memories, new MemoryTextFormatter(), 20).contextFor("italo", "bebida"))
            .contains("predicado: não gosta de; valor: café");
    }
}
