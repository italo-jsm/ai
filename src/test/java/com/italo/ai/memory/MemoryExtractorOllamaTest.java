package com.italo.ai.memory;

import com.italo.ai.memory.service.MemoryExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_OLLAMA_TESTS", matches = "true")
class MemoryExtractorOllamaTest {
    @Autowired MemoryExtractor extractor;
    @Autowired org.springframework.ai.chat.client.ChatClient chatClient;

    @Test void preservesNegationWithoutInferringPartyAffiliation() {
        assertThat(extractor.extract("Eu nao gosto de petista.").memories()).singleElement().satisfies(memory -> {
            assertThat(memory.subject()).isEqualTo("usuário");
            assertThat(memory.predicate()).isEqualTo("não gosta de");
            assertThat(memory.value()).isEqualTo("petista");
        });
    }
    @Test void ignoresQuestionsWithoutPersonalFacts() {
        for (String question : new String[]{"Que horas sao?", "Quem sou eu?", "Me conte o que vc sabe sobre mim"})
            assertThat(extractor.extract(question).memories()).as(question).isEmpty();
    }
    @Test void extractsOnlyExplicitFactInMixedMessage() {
        assertThat(extractor.extract("Meu nome é Italo. Que horas são?").memories()).singleElement().satisfies(memory -> {
            assertThat(memory.predicate()).isEqualTo("se chama");
            assertThat(memory.value()).isEqualTo("Italo");
        });
    }
    @Test void answersWithNegationFromLegacyDislikeMemory() {
        var memories = org.mockito.Mockito.mock(com.italo.ai.memory.service.MemoryService.class);
        var memory = new com.italo.ai.memory.service.MemoryService.MemoryView(java.util.UUID.randomUUID(), "italo",
            java.time.Instant.now(), com.italo.ai.memory.model.MemoryType.PREFERENCE, "petista", "DISLIKE", "true", null);
        org.mockito.Mockito.when(memories.search("italo", null, null, null, null,
            org.springframework.data.domain.PageRequest.of(0, 20)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(memory)));
        var context = new com.italo.ai.memory.service.MemoryContextService(memories, 20).contextFor("italo");
        var answer = chatClient.prompt()
            .options(org.springframework.ai.chat.prompt.ChatOptions.builder().temperature(0.0))
            .messages(new org.springframework.ai.chat.messages.SystemMessage(context))
            .user("Me conte o que vc sabe sobre mim").call().content();
        assertThat(answer).containsPattern("(?i)(não gosta|desgosta|não aprecia|não tem apreço)");
    }
}
