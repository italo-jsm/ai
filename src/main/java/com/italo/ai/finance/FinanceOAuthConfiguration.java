package com.italo.ai.finance;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.finance.oauth.enabled", havingValue = "true")
@EnableConfigurationProperties(FinanceOAuthSettings.class)
class FinanceOAuthConfiguration {
    @Bean
    FinanceOAuth financeOAuth(FinanceOAuthSettings settings) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(Duration.ofSeconds(10));
        var decoder = NimbusJwtDecoder.withJwkSetUri(settings.issuer() + "/protocol/openid-connect/certs")
                .restOperations(new RestTemplate(requests)).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(settings.issuer()));
        return new FinanceOAuth(settings, RestClient.builder().requestFactory(requests).build(), decoder);
    }

    @Bean
    FinanceLinks financeLinks(FinanceOAuthSettings settings, FinanceOAuth oauth, JdbcClient database) {
        return new FinanceLinks(settings, oauth, database, new FinanceTokenEncryption(settings.encryptionKey()));
    }
}
