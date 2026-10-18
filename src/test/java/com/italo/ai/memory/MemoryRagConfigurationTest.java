package com.italo.ai.memory;

import com.italo.ai.config.MemoryRagConfiguration;
import com.italo.ai.memory.persistence.MemoryRepository;
import com.italo.ai.memory.retrieval.*;
import com.italo.ai.memory.indexing.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemoryRagConfigurationTest {
    @Test void defaultsToSql() {
        try (var context = context(Map.of())) {
            context.refresh();
            assertThat(context.getBean(MemoryRetriever.class)).isInstanceOf(SqlMemoryRetriever.class);
        }
    }
    @Test void selectsSemantic() {
        try (var context = context(Map.of("app.memory.retrieval-strategy", "semantic"))) {
            context.refresh();
            assertThat(context.getBean(MemoryRetriever.class)).isInstanceOf(SemanticMemoryRetriever.class);
        }
    }
    @Test void unknownStrategyFailsStartup() {
        try (var context = context(Map.of("app.memory.retrieval-strategy", "unknown"))) {
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void indexingIsDisabledByDefault() {
        try (var context = context(Map.of())) {
            context.refresh();
            assertThat(context.getBean(MemoryIndexer.class)).isInstanceOf(NoOpMemoryIndexer.class);
        }
    }
    @Test void indexingCanBeEnabledWhileRetrievalRemainsSql() {
        try (var context = context(Map.of("app.memory.indexing.enabled", "true"))) {
            context.refresh();
            assertThat(context.getBean(MemoryIndexer.class)).isInstanceOf(VectorMemoryIndexer.class);
            assertThat(context.getBean(MemoryRetriever.class)).isInstanceOf(SqlMemoryRetriever.class);
        }
    }
    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.registerBean(MemoryRepository.class, () -> mock(MemoryRepository.class));
        context.registerBean(VectorStore.class, () -> mock(VectorStore.class));
        context.register(MemoryRagConfiguration.class);
        return context;
    }
}
