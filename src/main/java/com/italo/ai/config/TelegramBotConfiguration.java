package com.italo.ai.config;

import com.italo.ai.telegram.TelegramBotClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramBotConfiguration {

    @Bean
    TelegramBotClient telegramBotClient(
            @Value("${app.telegram.bot-token:}") String token,
            @Value("${app.telegram.base-url:https://api.telegram.org}") String baseUrl,
            @Value("${app.telegram.timeout:10s}") Duration timeout) {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("O timeout do Telegram deve ser positivo.");
        }
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new TelegramBotClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build(), token);
    }
}
