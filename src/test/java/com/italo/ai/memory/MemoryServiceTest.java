package com.italo.ai.memory;
import static com.italo.ai.support.TestUsers.*;
import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import com.italo.ai.memory.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import com.italo.ai.memory.retrieval.SemanticMemoryRetriever;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryServiceTest {
    @Test void rejectsMissingOwnerBeforeSaving() {
        var service = new MemoryService(mock(MemoryRepository.class), mock(SemanticMemoryRetriever.class));
        assertThatThrownBy(() -> service.save(null, new MemoryExtraction(List.of())))
            .isInstanceOf(NullPointerException.class);
    }
    @Test void savesCandidateFieldsAndReturnsMemoryIdentity() {
        var repository = mock(MemoryRepository.class);
        when(repository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var service = new MemoryService(repository, mock(SemanticMemoryRetriever.class));
        var result = service.save(ALICE, new MemoryExtraction(List.of(new MemoryCandidate(MemoryType.FACT, "alice", "vive em", "Recife", "explícito"))));
        assertThat(result).singleElement().satisfies(memory -> {
            assertThat(memory.getId()).isNotNull();
            assertThat(memory.getValue()).isEqualTo("Recife");
            assertThat(memory.getUserId()).isEqualTo(ALICE.getId());
        });
    }
    @Test void semanticSearchReturnsOnlyOwnedDatabaseRecordsInRelevanceOrder() {
        var repository = mock(MemoryRepository.class);
        var vectorStore = mock(VectorStore.class);
        var candidates = List.of(
            new MemoryCandidate(MemoryType.FACT, "alice", "vive em", "Recife", null),
            new MemoryCandidate(MemoryType.PREFERENCE, "alice", "prefere", "café", null));
        var first = new MemoryEntity(ALICE, candidates.get(0));
        var second = new MemoryEntity(ALICE, candidates.get(1));
        var foreignId = java.util.UUID.randomUUID();
        when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(
            org.springframework.ai.document.Document.builder().id(second.getId().toString()).text("café").build(),
            org.springframework.ai.document.Document.builder().id(foreignId.toString()).text("other user").build(),
            org.springframework.ai.document.Document.builder().id(first.getId().toString()).text("Recife").build()));
        when(repository.findByIdInAndUserId(any(), eq(ALICE.getId()))).thenReturn(List.of(first, second));
        var service = new MemoryService(repository, new SemanticMemoryRetriever(repository, vectorStore));
        assertThat(service.semanticSearch(ALICE.getId(), "gostos", 3)).extracting(MemoryService.MemoryView::id)
            .containsExactly(second.getId(), first.getId());
    }
}
