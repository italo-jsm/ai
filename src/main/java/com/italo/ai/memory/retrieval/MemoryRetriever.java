package com.italo.ai.memory.retrieval;

import java.util.UUID;
import com.italo.ai.memory.service.MemoryService.MemoryView;
import java.util.List;

public interface MemoryRetriever {
    List<MemoryView> retrieve(UUID userId, String message, int limit);

    static void validate(UUID userId, String message, int limit) {
        if (userId == null) throw new IllegalArgumentException("Informe o ID do usuário.");
        if (message == null || message.isBlank() || limit < 1 || limit > 100)
            throw new IllegalArgumentException("Informe mensagem e limite entre 1 e 100");
    }
}
