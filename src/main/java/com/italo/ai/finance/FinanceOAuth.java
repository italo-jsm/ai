package com.italo.ai.finance;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

class FinanceOAuth {
    private final FinanceOAuthSettings settings;
    private final RestClient http;
    private final JwtDecoder decoder;

    FinanceOAuth(FinanceOAuthSettings settings, RestClient http, JwtDecoder decoder) {
        this.settings = settings;
        this.http = http;
        this.decoder = decoder;
    }

    URI authorizationUri(String state, String verifier) {
        return UriComponentsBuilder.fromUriString(settings.issuer() + "/protocol/openid-connect/auth")
                .queryParam("response_type", "code")
                .queryParam("prompt", "login")
                .queryParam("client_id", "{client}")
                .queryParam("redirect_uri", "{callback}")
                .queryParam("scope", "openid")
                .queryParam("state", "{state}")
                .queryParam("nonce", "{nonce}")
                .queryParam("code_challenge", "{challenge}")
                .queryParam("code_challenge_method", "S256")
                .encode()
                .buildAndExpand(Map.of("client", settings.clientId(), "callback", settings.redirectUri(),
                        "state", state, "nonce", nonce(verifier), "challenge", FinanceLinks.hash(verifier))).toUri();
    }

    Tokens exchange(String code, String verifier) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", settings.redirectUri().toString());
        form.add("code_verifier", verifier);
        var response = token(form);
        var id = decode(response.idToken());
        if (!nonce(verifier).equals(id.getClaimAsString("nonce")) || id.getAudience() == null
                || !id.getAudience().contains(settings.clientId())
                || ((id.getAudience().size() > 1 || id.hasClaim("azp"))
                && !settings.clientId().equals(id.getClaimAsString("azp")))) {
            throw new InvalidAuthorization();
        }
        return validate(response, id.getSubject(), null);
    }

    Tokens refresh(String subject, String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return validate(token(form), subject, refreshToken);
    }

    private Tokens validate(TokenResponse response, String subject, String previousRefresh) {
        var access = decode(response.accessToken());
        var tenant = access.getClaims().get("tenant-id");
        var realm = access.getClaims().get("realm_access");
        String refresh = response.refreshToken() == null ? previousRefresh : response.refreshToken();
        if (subject == null || subject.isBlank() || !subject.equals(access.getSubject())
                || !settings.clientId().equals(access.getClaimAsString("azp"))
                || !(tenant instanceof String tenantId) || tenantId.isBlank()
                || !(realm instanceof Map<?, ?> roleClaims)
                || !(roleClaims.get("roles") instanceof Collection<?> roles) || !roles.contains("user")
                || refresh == null || refresh.isBlank()) {
            throw new InvalidAuthorization();
        }
        return new Tokens(subject, response.accessToken(), refresh, access.getExpiresAt());
    }

    private Jwt decode(String token) {
        if (token == null || token.isBlank()) throw new InvalidAuthorization();
        try {
            var jwt = decoder.decode(token);
            if (jwt.getExpiresAt() == null || !Instant.now().isBefore(jwt.getExpiresAt())) {
                throw new InvalidAuthorization();
            }
            return jwt;
        } catch (org.springframework.security.oauth2.jwt.BadJwtException rejected) {
            throw new InvalidAuthorization();
        } catch (JwtException unavailable) {
            throw new OAuthUnavailable();
        }
    }

    private TokenResponse token(MultiValueMap<String, String> form) {
        try {
            var response = http.post().uri(settings.issuer() + "/protocol/openid-connect/token")
                    .headers(headers -> headers.setBasicAuth(settings.clientId(), settings.clientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form).retrieve().body(TokenResponse.class);
            if (response == null || !"Bearer".equalsIgnoreCase(response.tokenType())) {
                throw new OAuthUnavailable();
            }
            return response;
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 400 && invalidGrant(failure.getResponseBodyAsString())) {
                throw new InvalidAuthorization();
            }
            throw new OAuthUnavailable();
        } catch (RestClientException failure) {
            throw new OAuthUnavailable();
        }
    }

    private boolean invalidGrant(String response) {
        try {
            return "invalid_grant".equals(JsonMapper.shared().readTree(response).path("error").asString());
        } catch (tools.jackson.core.JacksonException malformed) {
            return false;
        }
    }

    private String nonce(String verifier) {
        return FinanceLinks.hash("nonce:" + verifier);
    }

    record Tokens(String subject, String access, String refresh, Instant expiresAt) {}
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("refresh_token") String refreshToken,
                                 @JsonProperty("id_token") String idToken,
                                 @JsonProperty("token_type") String tokenType) {}

    static class InvalidAuthorization extends RuntimeException {}
    static class OAuthUnavailable extends RuntimeException {}
}
