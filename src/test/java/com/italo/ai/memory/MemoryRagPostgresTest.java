package com.italo.ai.memory;

import com.italo.ai.api.ChatController;
import com.italo.ai.memory.indexing.MemoryIndexer;
import com.italo.ai.memory.model.*;
import com.italo.ai.memory.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "app.memory.retrieval-strategy=semantic",
    "app.memory.indexing.enabled=true",
    "spring.ai.model.embedding=none",
    "spring.ai.openai.api-key=integration-test-key",
    "spring.ai.openai.chat.model=integration-test-model"
})
@Import(MemoryRagPostgresTest.TestEmbeddings.class)
@EnabledIfEnvironmentVariable(named = "RUN_RAG_POSTGRES_TESTS", matches = "true")
class MemoryRagPostgresTest {
    @Autowired ChatController controller;
    @Autowired MemoryService memories;
    @Autowired VectorStore vectors;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @MockitoBean ChatModel chatModel;
    @MockitoBean MemoryExtractor extractor;
    @MockitoSpyBean MemoryIndexer indexer;

    private CompletableFuture<Void> indexingFinished;

    private final String owner = "rag-test-" + UUID.randomUUID();
    private final String other = "rag-test-" + UUID.randomUUID();

    @BeforeEach void prepareModel() {
        when(chatModel.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Resposta")))));
    }

    @AfterEach void cleanup() throws Exception {
        if (indexingFinished != null)
            indexingFinished.handle((result, failure) -> null).get(30, TimeUnit.SECONDS);
        jdbc.update("delete from vector_store where metadata->>'userId' in (?, ?)", owner, other);
        jdbc.update("delete from memory where user_id in (?, ?)", owner, other);
    }

    @Test void commitsBeforeIndexingAndRetrievesOwnedSqlContentWithoutDuplicateVectors() throws Exception {
        var indexed = new CompletableFuture<Void>();
        indexingFinished = indexed;
        var extraction = preference("café sem açúcar");
        when(extractor.extract("Prefiro café sem açúcar")).thenReturn(extraction);
        doAnswer(invocation -> {
            try {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                List<MemoryService.MemoryView> saved = invocation.getArgument(0);
                try (var connection = dataSource.getConnection();
                     var statement = connection.prepareStatement("select count(*) from memory where id = ?")) {
                    statement.setObject(1, saved.getFirst().id());
                    try (var result = statement.executeQuery()) {
                        result.next();
                        assertThat(result.getInt(1)).isEqualTo(1);
                    }
                }
                var result = invocation.callRealMethod();
                indexed.complete(null);
                return result;
            } catch (Throwable failure) {
                indexed.completeExceptionally(failure);
                throw failure;
            }
        }).when(indexer).index(anyList());
        assertThat(controller.chat(new ChatController.ChatRequest(owner, "Prefiro café sem açúcar")).response()).isEqualTo("Resposta");
        indexed.get(5, TimeUnit.SECONDS);
        var saved = memories.semanticSearch(owner, "bebida", 20);
        assertThat(saved).singleElement().satisfies(memory -> assertThat(memory.value()).isEqualTo("café sem açúcar"));
        var id = saved.getFirst().id();

        var foreign = memories.save(other, preference("café com açúcar")).stream().map(MemoryService.MemoryView::from).toList();
        indexer.index(foreign);
        indexer.index(saved);
        assertThat(jdbc.queryForObject("select count(*) from vector_store where id = ?", Integer.class, id)).isEqualTo(1);

        // Vector content may be stale; only the official SQL record enters the context.
        vectors.add(List.of(Document.builder().id(id.toString()).text("café: texto vetorial divergente")
            .metadata(Map.of("userId", owner, "kind", "memory")).build()));
        assertThat(memories.semanticSearch(owner, "bebida", 20)).extracting(MemoryService.MemoryView::value)
            .containsExactly("café sem açúcar");
        assertThat(memories.semanticSearch(other, "bebida", 20)).extracting(MemoryService.MemoryView::userId)
            .containsOnly(other);
    }

    @Test void indexingFailureLeavesCommittedMemoryAndReturnsAnswer() throws Exception {
        when(extractor.extract("Prefiro café")).thenReturn(preference("café"));
        var attempted = new CompletableFuture<Void>();
        doAnswer(invocation -> {
            attempted.complete(null);
            throw new IllegalStateException("offline");
        }).when(indexer).index(anyList());
        assertThat(controller.chat(new ChatController.ChatRequest(owner, "Prefiro café")).response()).isEqualTo("Resposta");
        attempted.get(5, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("select count(*) from memory where user_id = ?", Integer.class, owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vector_store where metadata->>'userId' = ?", Integer.class, owner)).isZero();
    }

    private MemoryExtraction preference(String value) {
        return new MemoryExtraction(List.of(new MemoryCandidate(MemoryType.PREFERENCE, "usuário", "prefere", value, null)));
    }

    @TestConfiguration
    static class TestEmbeddings {
        @Bean EmbeddingModel deterministicEmbeddingModel() {
            return new EmbeddingModel() {
                @Override public float[] embed(Document document) {
                    return vector(document.getText());
                }
                @Override public EmbeddingResponse call(EmbeddingRequest request) {
                    var results = new ArrayList<Embedding>();
                    for (int i = 0; i < request.getInstructions().size(); i++)
                        results.add(new Embedding(vector(request.getInstructions().get(i)), i));
                    return new EmbeddingResponse(results);
                }
                @Override public int dimensions() { return 768; }
                private float[] vector(String text) {
                    var result = new float[768];
                    result[text.contains("café") || text.contains("bebida") ? 0 : 1] = 1;
                    return result;
                }
            };
        }
    }
}
