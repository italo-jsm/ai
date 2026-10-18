package com.italo.ai.memory.retrieval;

import com.italo.ai.memory.persistence.*;
import com.italo.ai.memory.service.MemoryService.MemoryView;
import org.springframework.ai.vectorstore.*;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import java.util.*;

public class SemanticMemoryRetriever implements MemoryRetriever {
    private final MemoryRepository memories;
    private final VectorStore vectorStore;

    public SemanticMemoryRetriever(MemoryRepository memories, VectorStore vectorStore) {
        this.memories = memories;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<MemoryView> retrieve(String userId, String message, int limit) {
        MemoryRetriever.validate(userId, message, limit);
        var filters = new FilterExpressionBuilder();
        var documents = vectorStore.similaritySearch(SearchRequest.builder().query(message).topK(limit)
            .filterExpression(filters.and(filters.eq("userId", userId), filters.eq("kind", "memory")).build()).build());
        if (documents.isEmpty()) return List.of();
        var ids = new LinkedHashSet<UUID>();
        for (var document : documents) {
            try {
                ids.add(UUID.fromString(document.getId()));
            } catch (IllegalArgumentException ignored) {
                // Ignore documents that cannot identify a SQL memory.
            }
        }
        if (ids.isEmpty()) return List.of();
        var found = new HashMap<UUID, MemoryEntity>();
        memories.findByIdInAndUserId(ids, userId).forEach(memory -> found.put(memory.getId(), memory));
        return ids.stream().map(found::get).filter(Objects::nonNull).limit(limit).map(MemoryView::from).toList();
    }
}
