package com.italo.ai.config;

import com.italo.ai.memory.service.MemoryExtractor;
import com.italo.ai.search.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:groq-config;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.ai.openai.max-retries=0"
})
class GroqConfigurationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<JsonNode> REQUESTS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean INCLUDE_REASONING = new AtomicBoolean();
    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer SERVER = server();

    @Autowired ChatModel model;
    @Autowired EmbeddingModel embeddings;
    @Autowired ChatClient chat;
    @Autowired MemoryExtractor extractor;
    @MockitoBean VectorStore vectors;
    @MockitoBean BraveSearchService brave;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("GROQ_API_KEY", () -> "groq-test-key");
        registry.add("GROQ_MODEL", () -> "groq-test-model");
        registry.add("spring.ai.openai.base-url",
            () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/openai/v1");
    }

    @BeforeEach void resetRequests() {
        REQUESTS.clear();
        INCLUDE_REASONING.set(false);
        when(brave.search("Fernando da Gata")).thenReturn(List.of(
            new SearchResult("Biografia", "https://example.com/biografia", "Trecho encontrado")));
    }

    @AfterAll static void stopServer() {
        SERVER.stop(0);
    }

    @Test void selectsGroqChatAndKeepsOllamaEmbeddings() {
        assertThat(model).isInstanceOf(OpenAiChatModel.class);
        assertThat(embeddings).isInstanceOf(OllamaEmbeddingModel.class);
        assertThat(REQUESTS).isEmpty();
    }

    @Test void callsGroqCompatibleEndpointAndExecutesBraveTool() {
        assertThat(chat.prompt().user("Quem foi Fernando da Gata?").call().content())
            .isEqualTo("Resposta com fonte https://example.com/biografia");
        assertThat(AUTHORIZATION).hasValue("Bearer groq-test-key");
        assertThat(REQUESTS).hasSize(2);
        assertThat(REQUESTS.getFirst().path("model").asString()).isEqualTo("groq-test-model");
        assertThat(REQUESTS.getFirst().path("tools").toString()).contains("searchWeb", "getLocalDateTime");
        var toolMessages = new ArrayList<String>();
        for (var message : REQUESTS.getLast().path("messages"))
            if (message.path("role").asString().equals("tool"))
                toolMessages.add(message.path("content").asString());
        assertThat(toolMessages).singleElement().asString().contains("https://example.com/biografia");
        verify(brave).search("Fernando da Gata");
    }

    @Test void toolCallingDoesNotReplayUnsupportedReasoningContent() {
        INCLUDE_REASONING.set(true);
        assertThat(chat.prompt().user("Quem foi Fernando da Gata?").call().content())
            .isEqualTo("Resposta com fonte https://example.com/biografia");
        assertThat(REQUESTS).hasSize(2);
        var assistants = new ArrayList<JsonNode>();
        for (var message : REQUESTS.getLast().path("messages"))
            if (message.path("role").asString().equals("assistant")) assistants.add(message);
        assertThat(assistants).singleElement().satisfies(message -> {
            assertThat(message.has("reasoning_content")).isFalse();
            assertThat(message.path("tool_calls").get(0).path("function").path("name").asString()).isEqualTo("searchWeb");
        });
        verify(brave).search("Fernando da Gata");
    }

    @Test void extractsMemoriesUsingSameGroqModelWithoutChatTools() {
        var extraction = extractor.extract("Prefiro café sem açúcar");
        assertThat(extraction.memories()).singleElement().satisfies(memory -> {
            assertThat(memory.predicate()).isEqualTo("gosta de");
            assertThat(memory.value()).isEqualTo("café sem açúcar");
        });
        assertThat(REQUESTS).hasSize(1);
        assertThat(REQUESTS.getFirst().path("model").asString()).isEqualTo("groq-test-model");
        assertThat(REQUESTS.getFirst().path("tools").size()).isZero();
    }

    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/openai/v1/chat/completions", exchange -> {
                var request = JSON.readTree(exchange.getRequestBody());
                REQUESTS.add(request);
                AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
                for (var item : request.path("messages")) {
                    if (item.path("role").asString().equals("assistant") && item.has("reasoning_content")) {
                        var error = "{\"error\":{\"message\":\"reasoning_content is unsupported\",\"type\":\"invalid_request_error\"}}"
                            .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(400, error.length);
                        try (var output = exchange.getResponseBody()) { output.write(error); }
                        return;
                    }
                }
                Map<String, Object> message;
                String finishReason = "stop";
                if (request.path("tools").isEmpty()) {
                    var content = """
                        {"memories":[{"type":"PREFERENCE","subject":"usuário","predicate":"gosta de",
                        "value":"café sem açúcar","reason":"Preferência explícita."}]}
                        """;
                    message = Map.of("role", "assistant", "content", content);
                } else {
                    boolean toolResult = false;
                    for (var item : request.path("messages"))
                        if (item.path("role").asString().equals("tool")) toolResult = true;
                    if (toolResult) {
                        message = Map.of("role", "assistant", "content", "Resposta com fonte https://example.com/biografia");
                    } else {
                        message = Map.of("role", "assistant", "tool_calls", List.of(Map.of(
                            "id", "call-brave", "type", "function", "function", Map.of(
                                "name", "searchWeb", "arguments", "{\"query\":\"Fernando da Gata\"}"))));
                        if (INCLUDE_REASONING.get()) {
                            message = new HashMap<>(message);
                            message.put("reasoning", "Metadado de raciocínio simulado.");
                        }
                        finishReason = "tool_calls";
                    }
                }
                var body = JSON.writeValueAsString(Map.of(
                    "id", "groq-test", "object", "chat.completion", "created", 1,
                    "model", "groq-test-model", "choices", List.of(Map.of(
                        "index", 0, "message", message, "finish_reason", finishReason)),
                    "usage", Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15)))
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException("Não foi possível iniciar o servidor de teste.", failure);
        }
    }
}
