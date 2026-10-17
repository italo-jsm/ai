package com.italo.ai.memory.model;

public record MemoryCandidate(
        MemoryType type,
        String subject,
        String predicate,
        String value,
        String reason
) {}

