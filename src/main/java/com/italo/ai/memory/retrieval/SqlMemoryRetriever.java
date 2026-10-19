package com.italo.ai.memory.retrieval;

import com.italo.ai.memory.persistence.MemoryRepository;
import com.italo.ai.memory.service.MemoryService.MemoryView;
import org.springframework.data.domain.PageRequest;
import java.util.List;

public class SqlMemoryRetriever implements MemoryRetriever {
    private final MemoryRepository memories;

    public SqlMemoryRetriever(MemoryRepository memories) {
        this.memories = memories;
    }

    @Override
    public List<MemoryView> retrieve(String userId, String message, int limit) {
        MemoryRetriever.validate(userId, message, limit);
        return memories.search(userId, null, null, null, null, PageRequest.of(0, limit))
            .map(MemoryView::from).getContent();
    }
}
