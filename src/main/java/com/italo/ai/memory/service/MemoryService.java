package com.italo.ai.memory.service;

import com.italo.ai.memory.model.*;
import com.italo.ai.memory.persistence.*;
import com.italo.ai.memory.retrieval.SemanticMemoryRetriever;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class MemoryService {
    private final MemoryRepository memories;
    private final SemanticMemoryRetriever semantic;

    public MemoryService(MemoryRepository memories, SemanticMemoryRetriever semantic) {
        this.memories = memories;
        this.semantic = semantic;
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

    public List<MemoryView> semanticSearch(String userId, String query, int limit) {
        return semantic.retrieve(userId, query, limit);
    }

    private static void requireOwner(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 255)
            throw new IllegalArgumentException("userId deve conter entre 1 e 255 caracteres");
    }

    public record MemoryView(UUID id, String userId, java.time.Instant createdAt,
                             MemoryType type, String subject, String predicate, String value, String reason) {
        public static MemoryView from(MemoryEntity memory) {
            return new MemoryView(memory.getId(), memory.getUserId(), memory.getCreatedAt(),
                memory.getType(), memory.getSubject(), memory.getPredicate(), memory.getValue(), memory.getReason());
        }
    }
}
