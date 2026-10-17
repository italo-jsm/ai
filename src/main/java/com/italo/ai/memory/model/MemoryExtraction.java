package com.italo.ai.memory.model;

import java.util.List;

public record MemoryExtraction(
        List<MemoryCandidate> memories
) {}
