
package com.italo.ai.search;

import com.italo.ai.config.BraveSearchConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;

class BraveSearchServiceTest {
    private HttpServer server;
    private AnnotationConfigApplicationContext context;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<URI> requestUri = new AtomicReference<>();
    private final AtomicReference<String> requestKey = new AtomicReference<>();
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private static final String KEY = "private-test-key";

    @BeforeEach void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach void cleanup() {
        releaseResponse.countDown();
        server.stop(0);
        if (context != null) context.close();
    }

    @Test void encodesQueryAndReturnsAtMostFiveWebResultsInOrder() {
        var results = new ArrayList<String>();
        for (int i = 1; i <= 6; i++)
            results.add("""
                {"title":"Título %d","url":"https://example.com/%d","description":"Descrição %d","extra_snippets":["ignorar"]}
                """.formatted(i, i, i));
        respond(200, "{\"type\":\"search\",\"query\":{},\"web\":{\"type\":\"search\",\"results\":[" + String.join(",", results) + "]}}");
        var query = "Fernando da Gata + café & história? #biografia";
        var found = service("10s", KEY).search("  " + query + "  ");
        assertThat(found).extracting(SearchResult::title)
            .containsExactly("Título 1", "Título 2", "Título 3", "Título 4", "Título 5");
        assertThat(found.getFirst()).isEqualTo(new SearchResult("Título 1", "https://example.com/1", "Descrição 1"));
        assertThat(requestUri.get().getPath()).isEqualTo("/res/v1/web/search");
        assertThat(parameters()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "q", query, "count", "5", "result_filter", "web", "text_decorations", "false"));
        assertThat(requestKey.get()).isEqualTo(KEY);
        assertThat(requests).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"web\":null}", "{\"web\":{}}", "{\"web\":{\"results\":[]}}"})
    void noWebResultsReturnsEmptyList(String body) {
        respond(200, body);
        assertThat(service("10s", KEY).search("consulta")).isEmpty();
    }

    @Test void optionalDescriptionDefaultsToEmptyString() {
        respond(200, "{\"web\":{\"results\":[{\"title\":\"Página\",\"url\":\"https://example.com\"}]}}");
        assertThat(service("10s", KEY).search("consulta")).containsExactly(new SearchResult("Página", "https://example.com", ""));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void invalidQueryNeverCallsProvider(String query) {
        assertThatIllegalArgumentException().isThrownBy(() -> service("10s", KEY).search(query));
        assertThat(requests).hasValue(0);
    }

    @Test void missingKeyFailsOnlyWhenSearching() {
        var search = service("10s", "");
        assertThatThrownBy(() -> search.search("consulta")).isInstanceOfSatisfying(BraveSearchException.class,
            error -> assertThat(error.reason()).isEqualTo(BraveSearchException.Reason.CONFIGURATION));
        assertThat(requests).hasValue(0);
    }

    @ParameterizedTest
    @CsvSource({"401,AUTHENTICATION", "403,AUTHENTICATION", "429,RATE_LIMIT", "500,UNAVAILABLE", "503,UNAVAILABLE", "400,REQUEST_FAILED"})
    void translatesHttpFailuresWithoutLeakingExternalContent(int status, BraveSearchException.Reason reason) {
        respond(status, KEY + " corpo externo privado");
        assertThatThrownBy(() -> service("10s", KEY).search("consulta privada"))
            .isInstanceOfSatisfying(BraveSearchException.class, error -> {
                assertThat(error.reason()).isEqualTo(reason);
                assertThat(error).hasNoCause();
                assertThat(error.getMessage()).doesNotContain(KEY, "consulta privada", "corpo externo privado");
            });
        assertThat(requests).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "", "null", "{\"web\":{\"results\":[null]}}",
        "{\"web\":{\"results\":[{\"title\":\"Página\"}]}}",
        "{\"web\":{\"results\":[{\"title\":\"\",\"url\":\"https://example.com\"}]}}"})
    void malformedResponseIsNotReportedAsEmptySearch(String body) {
        respond(200, body);
        assertThatThrownBy(() -> service("10s", KEY).search("consulta"))
            .isInstanceOfSatisfying(BraveSearchException.class,
                error -> assertThat(error.reason()).isEqualTo(BraveSearchException.Reason.INVALID_RESPONSE));
    }

    @Test void slowProviderTimesOutWithoutRetry() {
        server.createContext("/res/v1/web/search", exchange -> {
            record(exchange);
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                reply(exchange, 200, "{}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        assertThatThrownBy(() -> service("500ms", KEY).search("consulta"))
            .isInstanceOfSatisfying(BraveSearchException.class,
                error -> assertThat(error.reason()).isEqualTo(BraveSearchException.Reason.TIMEOUT));
        assertThat(requests).hasValue(1);
    }

    @Test void stalledBodyAfterHeadersIsAlsoReportedAsTimeout() {
        server.createContext("/res/v1/web/search", exchange -> {
            record(exchange);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write('{');
                output.flush();
                releaseResponse.await(5, TimeUnit.SECONDS);
                output.write("\"web\":{}}".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException cancelledResponse) {
                // The client closes the response when its deadline expires.
            }
        });
        assertThatThrownBy(() -> service("500ms", KEY).search("consulta"))
            .isInstanceOfSatisfying(BraveSearchException.class,
                error -> assertThat(error.reason()).isEqualTo(BraveSearchException.Reason.TIMEOUT));
        assertThat(requests).hasValue(1);
    }

    @Test void unavailableProviderIsReportedWithoutRawTransportError() {
        var search = service("500ms", KEY);
        server.stop(0);
        assertThatThrownBy(() -> search.search("consulta"))
            .isInstanceOfSatisfying(BraveSearchException.class, error -> {
                assertThat(error.reason()).isEqualTo(BraveSearchException.Reason.UNAVAILABLE);
                assertThat(error).hasNoCause();
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"0s", "-1s"})
    void rejectsNonPositiveTimeout(String timeout) {
        assertThatThrownBy(() -> service(timeout, KEY)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private BraveSearchService service(String timeout, String key) {
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().setConversionService(org.springframework.boot.convert.ApplicationConversionService.getSharedInstance());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "app.brave-search.api-key", key,
            "app.brave-search.base-url", "http://127.0.0.1:" + server.getAddress().getPort(),
            "app.brave-search.timeout", timeout)));
        context.register(BraveSearchConfiguration.class);
        context.refresh();
        return context.getBean(BraveSearchService.class);
    }

    private void respond(int status, String body) {
        server.createContext("/res/v1/web/search", exchange -> {
            record(exchange);
            reply(exchange, status, body);
        });
    }

    private void record(HttpExchange exchange) {
        requests.incrementAndGet();
        requestUri.set(exchange.getRequestURI());
        requestKey.set(exchange.getRequestHeaders().getFirst("X-Subscription-Token"));
    }

    private void reply(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private Map<String, String> parameters() {
        var result = new HashMap<String, String>();
        for (var pair : requestUri.get().getRawQuery().split("&")) {
            var parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
