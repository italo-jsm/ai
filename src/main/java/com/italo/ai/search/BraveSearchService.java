package com.italo.ai.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.*;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.time.Duration;
import static com.italo.ai.search.BraveSearchException.Reason.*;

public class BraveSearchService {
    private static final int RESULT_LIMIT = 5;
    private final RestClient client;
    private final String apiKey;
    private final Duration timeout;

    public BraveSearchService(RestClient client, String apiKey, Duration timeout) {
        this.client = client;
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank())
            throw new IllegalArgumentException("Informe um termo de pesquisa.");
        if (apiKey == null || apiKey.isBlank())
            throw new BraveSearchException(CONFIGURATION);
        long started = System.nanoTime();
        try {
            var response = client.get()
                .uri(builder -> builder.path("/res/v1/web/search")
                    .queryParam("q", "{query}").queryParam("count", RESULT_LIMIT)
                    .queryParam("result_filter", "web").queryParam("text_decorations", false)
                    .build(query.strip()))
                .header("X-Subscription-Token", apiKey)
                .retrieve().body(BraveResponse.class);
            if (response == null) throw new BraveSearchException(INVALID_RESPONSE);
            if (response.web() == null || response.web().results() == null) return List.of();
            List<SearchResult> list = response.web().results().stream().limit(RESULT_LIMIT)
                    .map(this::toResult).toList();
            return list;
        } catch (RestClientResponseException failure) {
            throw new BraveSearchException(reasonForStatus(failure.getStatusCode().value()));
        } catch (ResourceAccessException failure) {
            throw new BraveSearchException(isTimeout(failure) || deadlineExpired(started) ? TIMEOUT : UNAVAILABLE);
        } catch (RestClientException failure) {
            // Spring may wrap a body-read timeout as a generic decoding failure.
            throw new BraveSearchException(isTimeout(failure) || deadlineExpired(started) ? TIMEOUT : INVALID_RESPONSE);
        }
    }

    private SearchResult toResult(WebResult result) {
        if (result == null || result.title() == null || result.title().isBlank()
                || result.url() == null || result.url().isBlank())
            throw new BraveSearchException(INVALID_RESPONSE);
        return new SearchResult(result.title(), result.url(), result.description() == null ? "" : result.description());
    }

    private BraveSearchException.Reason reasonForStatus(int status) {
        if (status == 401 || status == 403) return AUTHENTICATION;
        if (status == 429) return RATE_LIMIT;
        if (status >= 500) return UNAVAILABLE;
        return REQUEST_FAILED;
    }

    private boolean deadlineExpired(long started) {
        return System.nanoTime() - started >= timeout.toNanos();
    }

    private boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) return true;
        return false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BraveResponse(WebResults web) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WebResults(List<WebResult> results) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WebResult(String title, String url, String description) {}
}
