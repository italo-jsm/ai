package com.italo.ai.memory;

import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import com.italo.ai.memory.retrieval.SemanticMemoryRetriever;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SemanticMemoryRetrieverTest {
    @Test void returnsOnlyOwnedSqlRecordsInRelevanceOrderAndSkipsBadIds() {
        var repository = mock(MemoryRepository.class);
        var vectors = mock(VectorStore.class);
        var first = new MemoryEntity("alice", new MemoryCandidate(MemoryType.FACT, "usuário", "vive em", "Recife", null));
        var second = new MemoryEntity("alice", new MemoryCandidate(MemoryType.PREFERENCE, "usuário", "prefere", "café", null));
        var foreignId = UUID.randomUUID();
        var orphanId = UUID.randomUUID();
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
            doc(second.getId().toString()), doc(foreignId.toString()), doc("bad-id"),
            doc(second.getId().toString()), doc(orphanId.toString()), doc(first.getId().toString())));
        when(repository.findByIdInAndUserId(any(), eq("alice"))).thenReturn(List.of(first, second));
        var result = new SemanticMemoryRetriever(repository, vectors).retrieve("alice", "meus gostos", 10);
        assertThat(result).extracting(m -> m.id()).containsExactly(second.getId(), first.getId());
        assertThat(result).extracting(m -> m.value()).containsExactly("café", "Recife");
        var request = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectors).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery()).isEqualTo("meus gostos");
        assertThat(request.getValue().getTopK()).isEqualTo(10);
        assertThat(request.getValue().getFilterExpression().toString()).contains("userId", "alice", "kind", "memory");
    }
    @Test void emptySearchReturnsNoContext() {
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        assertThat(new SemanticMemoryRetriever(mock(MemoryRepository.class), vectors).retrieve("alice", "query", 20)).isEmpty();
    }
    @Test void failurePropagatesWithoutFallback() {
        var vectors = mock(VectorStore.class);
        when(vectors.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> new SemanticMemoryRetriever(mock(MemoryRepository.class), vectors).retrieve("alice", "query", 20))
            .isInstanceOf(IllegalStateException.class).hasMessage("offline");
    }
    @Test void rejectsInvalidInputBeforeEmbedding() {
        var vectors = mock(VectorStore.class);
        var retriever = new SemanticMemoryRetriever(mock(MemoryRepository.class), vectors);
        assertThatIllegalArgumentException().isThrownBy(() -> retriever.retrieve(null, "query", 20));
        assertThatIllegalArgumentException().isThrownBy(() -> retriever.retrieve("alice", "", 20));
        assertThatIllegalArgumentException().isThrownBy(() -> retriever.retrieve("alice", "query", 0));
        verifyNoInteractions(vectors);
    }
    private Document doc(String id) {
        return Document.builder().id(id).text("conteúdo vetorial não deve substituir SQL").build();
    }
}
