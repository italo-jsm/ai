package com.italo.ai.memory.service;

import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import org.springframework.ai.vectorstore.*;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class MemoryService {
    private final MemoryRepository memories;
    private final VectorStore vectorStore;

    public MemoryService(MemoryRepository memories, VectorStore vectorStore) {
        this.memories = memories;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public List<MemoryEntity> save(String userId, MemoryExtraction extraction) {
        requireOwner(userId);
        Objects.requireNonNull(extraction, "extraction");
        var entities = Objects.requireNonNull(extraction.memories(), "memories").stream()
            .map(candidate -> new MemoryEntity(userId, candidate)).toList();
        if (entities.isEmpty()) return List.of();
        return memories.saveAllAndFlush(entities);
    }

    @Transactional(readOnly = true)
    public Page<MemoryView> search(String userId, MemoryType type, String subject, String predicate, String text, Pageable pageable) {
        requireOwner(userId);
        return memories.search(userId, type, subject, predicate, text, pageable).map(MemoryView::from);
    }

    @Transactional(readOnly = true)
    public List<MemoryView> semanticSearch(String userId, String query, int limit) {
        requireOwner(userId);
        if (query == null || query.isBlank() || limit < 1 || limit > 100)
            throw new IllegalArgumentException("Informe query e limit entre 1 e 100");
        var filters = new FilterExpressionBuilder();
        var documents = vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(limit)
            .filterExpression(filters.and(filters.eq("userId", userId), filters.eq("kind", "memory")).build()).build());
        if (documents == null || documents.isEmpty()) return List.of();
        var ids = documents.stream().map(document -> UUID.fromString(document.getId())).toList();
        var found = new HashMap<UUID, MemoryEntity>();
        memories.findByIdInAndUserId(ids, userId).forEach(memory -> found.put(memory.getId(), memory));
        return ids.stream().map(found::get).filter(Objects::nonNull).map(MemoryView::from).toList();
    }

    private static void requireOwner(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 255)
            throw new IllegalArgumentException("userId deve conter entre 1 e 255 caracteres");
    }

    public record MemoryView(UUID id, String userId, java.time.Instant createdAt,
                             MemoryType type, String subject, String predicate, String value, String reason) {
        static MemoryView from(MemoryEntity memory) {
            return new MemoryView(memory.getId(), memory.getUserId(), memory.getCreatedAt(),
                memory.getType(), memory.getSubject(), memory.getPredicate(), memory.getValue(), memory.getReason());
        }
    }
}
