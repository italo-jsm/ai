package com.italo.ai.memory;

import com.italo.ai.memory.indexing.VectorMemoryIndexer;
import com.italo.ai.memory.model.MemoryType;
import com.italo.ai.memory.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VectorMemoryIndexerTest {
    @Test void indexesSqlIdentityOwnerAndNegationWithoutInternalFields() {
        var store = mock(VectorStore.class);
        var memory = new MemoryService.MemoryView(UUID.randomUUID(), "alice", Instant.now(),
            MemoryType.PREFERENCE, "café", "DISLIKE", "true", "motivo privado");
        new VectorMemoryIndexer(store, new MemoryTextFormatter()).index(List.of(memory));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
        verify(store).add(documents.capture());
        assertThat(documents.getValue()).singleElement().satisfies(document -> {
            assertThat(document.getId()).isEqualTo(memory.id().toString());
            assertThat(document.getText()).contains("não gosta de café").doesNotContain("motivo privado", memory.id().toString());
            assertThat(document.getMetadata()).containsEntry("userId", "alice")
                .containsEntry("kind", "memory").containsEntry("type", "PREFERENCE");
        });
    }
    @Test void emptyExtractionDoesNotCallEmbeddings() {
        var store = mock(VectorStore.class);
        new VectorMemoryIndexer(store, new MemoryTextFormatter()).index(List.of());
        verifyNoInteractions(store);
    }
}
