package com.italo.ai.memory;
import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import com.italo.ai.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryServiceTest {
    @Test void rejectsMissingOwnerBeforeSaving() {
        var service = new MemoryService(mock(MemoryRepository.class), mock(VectorStore.class));
        assertThatThrownBy(() -> service.save(" ", new MemoryExtraction(List.of())))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void savesCandidateFieldsAndReturnsMemoryIdentity() {
        var repository = mock(MemoryRepository.class);
        when(repository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var service = new MemoryService(repository, mock(VectorStore.class));
        var result = service.save("alice", new MemoryExtraction(List.of(new MemoryCandidate(MemoryType.FACT, "alice", "vive em", "Recife", "explícito"))));
        assertThat(result).singleElement().satisfies(memory -> {
            assertThat(memory.getId()).isNotNull();
            assertThat(memory.getValue()).isEqualTo("Recife");
            assertThat(memory.getUserId()).isEqualTo("alice");
        });
    }
    @Test void semanticSearchReturnsOnlyOwnedDatabaseRecordsInRelevanceOrder() {
        var repository = mock(MemoryRepository.class);
        var vectorStore = mock(VectorStore.class);
        var candidates = List.of(
            new MemoryCandidate(MemoryType.FACT, "alice", "vive em", "Recife", null),
            new MemoryCandidate(MemoryType.PREFERENCE, "alice", "prefere", "café", null));
        var first = new MemoryEntity("alice", candidates.get(0));
        var second = new MemoryEntity("alice", candidates.get(1));
        var foreignId = java.util.UUID.randomUUID();
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(
            org.springframework.ai.document.Document.builder().id(second.getId().toString()).text("café").build(),
            org.springframework.ai.document.Document.builder().id(foreignId.toString()).text("other user").build(),
            org.springframework.ai.document.Document.builder().id(first.getId().toString()).text("Recife").build()));
        when(repository.findByIdInAndUserId(any(), eq("alice"))).thenReturn(List.of(first, second));
        var service = new MemoryService(repository, vectorStore);
        assertThat(service.semanticSearch("alice", "gostos", 3)).extracting(MemoryService.MemoryView::id)
            .containsExactly(second.getId(), first.getId());
    }
}
