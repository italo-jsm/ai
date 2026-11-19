package com.italo.ai.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FinanceOAuthTest {
    private static final String ISSUER = "https://keycloak.example/realms/finance";
    private static final String CALLBACK = "http://localhost:8080/login/oauth2/code/finance";
    private static final String VERIFIER = "test-verifier";
    private MockRestServiceServer server;
    private FinanceOAuth oauth;
    private KeyPair key;

    @BeforeEach
    void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        key = generator.generateKeyPair();
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) key.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        oauth = new FinanceOAuth(new FinanceOAuthSettings(ISSUER, "ai-finance", "secret", URI.create(CALLBACK), null),
                builder.build(), decoder);
    }

    @Test
    void exchangesCodeWithPkceAndVerifiesTheUserTokens() throws Exception {
        String nonce = FinanceLinks.hash("nonce:" + VERIFIER);
        String idToken = sign(claims("alice").audience("ai-finance").claim("nonce", nonce));
        String access = sign(accessClaims("alice", "tenant-a"));
        var authorization = oauth.authorizationUri("state", VERIFIER);

        assertThat(authorization.getQuery()).contains("state=state", "nonce=" + nonce, "code_challenge_method=S256",
                "code_challenge=" + FinanceLinks.hash(VERIFIER), "redirect_uri=" + CALLBACK);
        server.expect(requestTo(ISSUER + "/protocol/openid-connect/token"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YWktZmluYW5jZTpzZWNyZXQ="))
                .andExpect(content().string("grant_type=authorization_code&code=code&redirect_uri="
                        + "http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Ffinance&code_verifier=test-verifier"))
                .andRespond(withSuccess(response(access, idToken), MediaType.APPLICATION_JSON));

        var result = oauth.exchange("code", VERIFIER);

        assertThat(result.subject()).isEqualTo("alice");
        assertThat(result.access()).isEqualTo(access);
        assertThat(result.refresh()).isEqualTo("refresh");
        server.verify();
    }

    @Test
    void rejectsRefreshWithDifferentUserOrMissingTenant() throws Exception {
        server.expect(requestTo(ISSUER + "/protocol/openid-connect/token"))
                .andExpect(content().string("grant_type=refresh_token&refresh_token=refresh"))
                .andRespond(withSuccess(response(sign(accessClaims("bob", "tenant-b")), ""), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ISSUER + "/protocol/openid-connect/token"))
                .andRespond(withSuccess(response(sign(accessClaims("alice", "")), ""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> oauth.refresh("alice", "refresh")).isInstanceOf(FinanceOAuth.InvalidAuthorization.class);
        assertThatThrownBy(() -> oauth.refresh("alice", "refresh")).isInstanceOf(FinanceOAuth.InvalidAuthorization.class);
        server.verify();
    }

    private JWTClaimsSet.Builder claims(String subject) {
        return new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject).issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(120)));
    }

    private JWTClaimsSet.Builder accessClaims(String subject, String tenant) {
        return claims(subject).claim("azp", "ai-finance").claim("tenant-id", tenant)
                .claim("realm_access", Map.of("roles", List.of("user")));
    }

    private String sign(JWTClaimsSet.Builder claims) throws JOSEException {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    private String response(String access, String idToken) {
        return """
                {"access_token":"%s","id_token":"%s","refresh_token":"refresh","token_type":"Bearer"}
                """.formatted(access, idToken);
    }
}
