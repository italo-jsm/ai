package com.italo.ai.memory.retrieval;

import com.italo.ai.memory.service.MemoryService.MemoryView;
import java.util.List;

public interface MemoryRetriever {
    List<MemoryView> retrieve(String userId, String message, int limit);

    static void validate(String userId, String message, int limit) {
        if (userId == null || userId.isBlank() || userId.length() > 255)
            throw new IllegalArgumentException("userId deve conter entre 1 e 255 caracteres");
        if (message == null || message.isBlank() || limit < 1 || limit > 100)
            throw new IllegalArgumentException("Informe mensagem e limite entre 1 e 100");
    }
}
