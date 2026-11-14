package com.italo.ai.config;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Contrato HTTP mínimo dos dois adaptadores; não executa inferência real. */
final class ProviderStub implements AutoCloseable {
    private final HttpServer server;
    final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    final List<String> authorizations = new CopyOnWriteArrayList<>();

    ProviderStub(boolean ollama) throws IOException {
        var json = JsonMapper.builder().build();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(ollama ? "/api/chat" : "/v1/chat/completions", exchange -> {
            var request = json.readTree(exchange.getRequestBody());
            requests.add(request);
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization") == null
                    ? "" : exchange.getRequestHeaders().getFirst("Authorization"));
            boolean hasTools = !request.path("tools").isEmpty();
            boolean hasResult = false;
            for (var item : request.path("messages"))
                if (item.path("role").asString().equals("tool")) hasResult = true;
            Map<String, Object> message;
            if (hasTools && !hasResult) {
                var function = Map.of("name", "getLocalDateTime", "arguments", ollama ? Map.of() : "{}");
                message = Map.of("role", "assistant", "content", "", "tool_calls",
                        List.of(Map.of("id", "call-time", "type", "function", "function", function)));
            } else {
                String content = hasTools ? "Horário consultado." : """
                        {"memories":[{"type":"IDENTITY","subject":"usuário","predicate":"se chama",
                        "value":"Italo","reason":"Nome explícito."}]}
                        """;
                message = Map.of("role", "assistant", "content", content);
            }
            Object response = ollama
                    ? Map.of("model", "local-test-model", "message", message, "done", true)
                    : Map.of("id", "test", "object", "chat.completion", "created", 1,
                            "model", "compatible-test-model", "choices", List.of(Map.of("index", 0,
                                    "message", message, "finish_reason", hasTools && !hasResult ? "tool_calls" : "stop")));
            byte[] body = json.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }

    String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    void clear() { requests.clear(); authorizations.clear(); }
    @Override public void close() { server.stop(0); }
}
