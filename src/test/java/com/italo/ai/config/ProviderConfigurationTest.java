package com.italo.ai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.env.Environment;
import org.springframework.ai.chat.client.ChatClient;
import com.italo.ai.memory.service.MemoryExtractor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:providers;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "app.telegram.enabled=false"
})
class ProviderConfigurationTest extends ProviderContractAssertions {
    static final ProviderStub SERVER = stub(true);
    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", SERVER::baseUrl);
        registry.add("OLLAMA_CHAT_MODEL", () -> "local-test-model");
    }
    @Override ProviderStub server() { return SERVER; }
    @Override String expectedModel() { return "local-test-model"; }
    @AfterAll static void stop() { SERVER.close(); }

    @Autowired ChatModel model;
    @Autowired EmbeddingModel embeddings;
    @Autowired Environment environment;
    @MockitoBean VectorStore vectors;

    @Test void defaultsToLocalChatWithoutCloudCredentials() {
        assertThat(model).isInstanceOf(OllamaChatModel.class);
        assertThat(embeddings).isInstanceOf(OllamaEmbeddingModel.class);
        assertThat(environment.getProperty("spring.ai.openai.api-key")).isNull();
    }
}

@ActiveProfiles("openai-compatible")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:compatible;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "app.telegram.enabled=false",
        "AI_API_KEY=compatible-test-key",
        "AI_MODEL=compatible-test-model"
})
class CompatibleProviderConfigurationTest extends ProviderContractAssertions {
    static final ProviderStub SERVER = stub(false);
    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry registry) {
        registry.add("AI_BASE_URL", () -> SERVER.baseUrl() + "/v1");
    }
    @Override ProviderStub server() { return SERVER; }
    @Override String expectedModel() { return "compatible-test-model"; }
    @AfterAll static void stop() { SERVER.close(); }

    @Autowired ChatModel model;
    @Autowired EmbeddingModel embeddings;
    @Autowired Environment environment;
    @MockitoBean VectorStore vectors;

    @Test void selectsCompatibleProviderWithoutGroqCredentials() {
        assertThat(model).isInstanceOf(OpenAiChatModel.class);
        assertThat(model.getOptions().getModel()).isEqualTo("compatible-test-model");
        assertThat(embeddings).isInstanceOf(OllamaEmbeddingModel.class);
        assertThat(environment.getProperty("app.groq.reasoning-history-filter-enabled")).isNull();
    }
}

abstract class ProviderContractAssertions {
    @Autowired ChatClient chat;
    @Autowired MemoryExtractor extractor;
    abstract ProviderStub server();
    abstract String expectedModel();
    static ProviderStub stub(boolean ollama) {
        try { return new ProviderStub(ollama); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    @BeforeEach void clearRequests() { server().clear(); }

    @Test void executesToolAndSendsItsResultBackToProvider() {
        assertThat(chat.prompt().user("Que horas são?").call().content()).isEqualTo("Horário consultado.");
        assertThat(server().requests).hasSize(2);
        assertThat(server().requests.getFirst().path("model").asString()).isEqualTo(expectedModel());
        assertThat(server().requests.getFirst().path("tools").toString()).contains("getLocalDateTime", "searchWeb");
        assertThat(server().requests.getLast().path("messages").toString()).contains("\"role\":\"tool\"");
        if (expectedModel().startsWith("compatible"))
            assertThat(server().authorizations).containsOnly("Bearer compatible-test-key");
    }

    @Test void extractsStructuredMemoriesWithoutTools() {
        assertThat(extractor.extract("Meu nome é Italo").memories()).singleElement()
                .satisfies(memory -> assertThat(memory.value()).isEqualTo("Italo"));
        assertThat(server().requests).hasSize(1);
        assertThat(server().requests.getFirst().path("model").asString()).isEqualTo(expectedModel());
        assertThat(server().requests.getFirst().path("tools").size()).isZero();
    }
}
