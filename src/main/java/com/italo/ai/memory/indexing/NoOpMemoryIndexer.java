package com.italo.ai.memory.indexing;

import com.italo.ai.memory.service.MemoryService.MemoryView;
import java.util.List;

public class NoOpMemoryIndexer implements MemoryIndexer {
    @Override
    public void index(List<MemoryView> memories) {
        // SQL-only operation: no derived index.
    }
}
