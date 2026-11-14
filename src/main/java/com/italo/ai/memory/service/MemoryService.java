package com.italo.ai.memory.service;

import com.italo.ai.memory.model.*;
import com.italo.ai.user.UserEntity;
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
    public List<MemoryEntity> save(UserEntity user, MemoryExtraction extraction) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(extraction, "extraction");
        var entities = Objects.requireNonNull(extraction.memories(), "memories").stream()
            .map(candidate -> new MemoryEntity(user, candidate)).toList();
        if (entities.isEmpty()) return List.of();
        return memories.saveAllAndFlush(entities);
    }

    @Transactional(readOnly = true)
    public Page<MemoryView> search(UUID userId, MemoryType type, String subject, String predicate, String text, Pageable pageable) {
        requireOwner(userId);
        return memories.search(userId, type, subject, predicate, text, pageable).map(MemoryView::from);
    }

    public List<MemoryView> semanticSearch(UUID userId, String query, int limit) {
        return semantic.retrieve(userId, query, limit);
    }

    private static void requireOwner(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("Informe o ID do usuário.");
    }

    public record MemoryView(UUID id, UUID userId, java.time.Instant createdAt,
                             MemoryType type, String subject, String predicate, String value, String reason) {
        public static MemoryView from(MemoryEntity memory) {
            return new MemoryView(memory.getId(), memory.getUserId(), memory.getCreatedAt(),
                memory.getType(), memory.getSubject(), memory.getPredicate(), memory.getValue(), memory.getReason());
        }
    }
}
