package com.italo.ai.config;

import com.italo.ai.finance.FinanceClient;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
public class FinanceConfiguration {
    private static final Logger log = LoggerFactory.getLogger(FinanceConfiguration.class);

    @Bean
    FinanceClient financeClient(
            @Value("${app.finance.base-url:https://finance.italomariano.dev.br}") String baseUrl,
            @Value("${app.finance.timeout:10s}") Duration timeout) {
        var httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        var client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    if (log.isDebugEnabled()) {
                        String payload = body.length == 0 ? "(sem corpo)" : new String(body, StandardCharsets.UTF_8);
                        log.debug("Finance {} {} body={}", request.getMethod(), request.getURI().getRawPath(), payload);
                    }
                    return execution.execute(request, body);
                })
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
                .build().createClient(FinanceClient.class);
    }
}
