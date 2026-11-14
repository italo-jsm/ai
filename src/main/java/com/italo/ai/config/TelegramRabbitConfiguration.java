package com.italo.ai.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.telegram.enabled", havingValue = "true")
public class TelegramRabbitConfiguration {

    @Bean
    public JacksonJsonMessageConverter telegramMessageConverter() {
        return new JacksonJsonMessageConverter("com.italo.ai.telegram.model");
    }
}
