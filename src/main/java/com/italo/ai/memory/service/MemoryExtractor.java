package com.italo.ai.memory.service;

import com.italo.ai.memory.model.MemoryExtraction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class MemoryExtractor {

    private final ChatClient chatClient;

    public MemoryExtractor(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public MemoryExtraction extract(String message) {
        return chatClient.prompt()
                .system("""
                    Extraia fatos explícitos e preferências
                    potencialmente úteis para conversas futuras.

                    Regras:
                    - Não invente fatos.
                    - Ignore perguntas pontuais.
                    - Ignore credenciais e segredos.
                    - Não infira dados sensíveis.
                    - Se não houver nada relevante,
                      retorne uma lista vazia.
                    """)
                .user(message)
                .call()
                .entity(MemoryExtraction.class);
    }
}
