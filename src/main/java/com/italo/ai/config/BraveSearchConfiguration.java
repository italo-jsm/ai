package com.italo.ai.config;

import com.italo.ai.search.BraveSearchService;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class BraveSearchConfiguration {
    @Bean(destroyMethod = "close")
    HttpClient braveSearchHttpClient(@Value("${app.brave-search.timeout:10s}") Duration timeout) {
        if (timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("O timeout da Brave Search deve ser positivo.");
        return HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Bean
    BraveSearchService braveSearchService(
            @Qualifier("braveSearchHttpClient") HttpClient httpClient,
            @Value("${app.brave-search.base-url:https://api.search.brave.com}") String baseUrl,
            @Value("${app.brave-search.api-key:}") String apiKey,
            @Value("${app.brave-search.timeout:10s}") Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        var client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE).build();
        return new BraveSearchService(client, apiKey, timeout);
    }
}
