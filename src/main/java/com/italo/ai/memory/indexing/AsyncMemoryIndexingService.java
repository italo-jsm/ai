package com.italo.ai.memory.indexing;

import com.italo.ai.memory.service.MemoryService.MemoryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncMemoryIndexingService {
    private static final Logger log = LoggerFactory.getLogger(AsyncMemoryIndexingService.class);
    private final MemoryIndexer indexer;

    public AsyncMemoryIndexingService(MemoryIndexer indexer) {
        this.indexer = indexer;
    }

    @Async
    public CompletableFuture<Void> index(List<MemoryView> memories) {
        try {
            indexer.index(memories);
        } catch (RuntimeException failure) {
            log.warn("Falha ao indexar {} memórias; tipo={}", memories.size(), failure.getClass().getSimpleName());
        }
        return CompletableFuture.completedFuture(null);
    }
}
