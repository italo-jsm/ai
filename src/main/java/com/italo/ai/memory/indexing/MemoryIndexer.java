package com.italo.ai.memory.indexing;

import com.italo.ai.memory.service.MemoryService.MemoryView;
import java.util.List;

public interface MemoryIndexer {
    void index(List<MemoryView> memories);
}
