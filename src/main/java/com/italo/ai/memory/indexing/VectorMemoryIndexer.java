package com.italo.ai.memory.indexing;

import com.italo.ai.memory.service.MemoryService.MemoryView;
import com.italo.ai.memory.service.MemoryTextFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import java.util.*;

public class VectorMemoryIndexer implements MemoryIndexer {
    private final VectorStore vectors;
    private final MemoryTextFormatter formatter;

    public VectorMemoryIndexer(VectorStore vectors, MemoryTextFormatter formatter) {
        this.vectors = vectors;
        this.formatter = formatter;
    }

    @Override
    public void index(List<MemoryView> memories) {
        if (memories.isEmpty()) return;
        var documents = memories.stream().map(memory -> Document.builder()
            .id(memory.id().toString()).text(formatter.format(memory))
            .metadata(Map.of("userId", memory.userId(), "kind", "memory", "type", memory.type().name()))
            .build()).toList();
        vectors.add(documents);
    }
}
