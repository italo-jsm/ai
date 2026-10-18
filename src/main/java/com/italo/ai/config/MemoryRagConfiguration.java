package com.italo.ai.config;

import com.italo.ai.memory.persistence.MemoryRepository;
import com.italo.ai.memory.retrieval.*;
import com.italo.ai.memory.indexing.*;
import com.italo.ai.memory.service.MemoryTextFormatter;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class MemoryRagConfiguration {
    @Bean
    MemoryTextFormatter memoryTextFormatter() {
        return new MemoryTextFormatter();
    }

    @Bean
    SemanticMemoryRetriever semanticMemoryRetriever(MemoryRepository memories, VectorStore vectorStore) {
        return new SemanticMemoryRetriever(memories, vectorStore);
    }

    @Bean
    @Primary
    MemoryRetriever memoryRetriever(MemoryRepository memories, SemanticMemoryRetriever semantic,
            @Value("${app.memory.retrieval-strategy:sql}") String strategy) {
        return switch (strategy) {
            case "sql" -> new SqlMemoryRetriever(memories);
            case "semantic" -> semantic;
            default -> throw new IllegalArgumentException("retrieval-strategy deve ser sql ou semantic");
        };
    }
    @Bean
    MemoryIndexer memoryIndexer(VectorStore vectors, MemoryTextFormatter formatter,
            @Value("${app.memory.indexing.enabled:false}") boolean enabled) {
        return enabled ? new VectorMemoryIndexer(vectors, formatter) : new NoOpMemoryIndexer();
    }
}
