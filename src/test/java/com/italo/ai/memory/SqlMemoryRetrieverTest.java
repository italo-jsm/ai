package com.italo.ai.memory;

import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import com.italo.ai.memory.retrieval.SqlMemoryRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlMemoryRetrieverTest {
    @Test void sqlReturnsLatestOwnedMemoriesWithinLimit() {
        var repository = mock(MemoryRepository.class);
        var entity = new MemoryEntity("alice", new MemoryCandidate(MemoryType.FACT, "usuário", "vive em", "Recife", null));
        when(repository.search("alice", null, null, null, null, PageRequest.of(0, 1)))
            .thenReturn(new PageImpl<>(List.of(entity)));
        assertThat(new SqlMemoryRetriever(repository).retrieve("alice", "onde moro?", 1))
            .singleElement().satisfies(memory -> {
                assertThat(memory.userId()).isEqualTo("alice");
                assertThat(memory.value()).isEqualTo("Recife");
            });
    }
    @Test void rejectsInvalidInputBeforeQuerying() {
        var repository = mock(MemoryRepository.class);
        var retriever = new SqlMemoryRetriever(repository);
        assertThatIllegalArgumentException().isThrownBy(() -> retriever.retrieve(" ", "query", 20));
        assertThatIllegalArgumentException().isThrownBy(() -> retriever.retrieve("alice", " ", 20));
        assertThatIllegalArgumentException().isThrownBy(() -> retriever.retrieve("alice", "query", 101));
        verifyNoInteractions(repository);
    }
}
