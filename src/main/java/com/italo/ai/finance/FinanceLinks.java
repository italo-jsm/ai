package com.italo.ai.finance;

import com.italo.ai.finance.FinanceOAuth.InvalidAuthorization;
import com.italo.ai.finance.FinanceOAuth.OAuthUnavailable;
import com.italo.ai.finance.FinanceOAuth.Tokens;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

public class FinanceLinks {
    static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final FinanceOAuthSettings settings;
    private final FinanceOAuth oauth;
    private final JdbcClient database;
    private final FinanceTokenEncryption encryption;

    FinanceLinks(FinanceOAuthSettings settings, FinanceOAuth oauth, JdbcClient database,
                 FinanceTokenEncryption encryption) {
        this.settings = settings;
        this.oauth = oauth;
        this.database = database;
        this.encryption = encryption;
    }

    @Transactional
    public URI createLink(UUID userId) {
        lockUser(userId);
        String state = randomValue();
        database.sql("DELETE FROM finance_link_request WHERE user_id = ?").param(userId).update();
        database.sql("""
                INSERT INTO finance_link_request (user_id, state_hash, verifier, expires_at)
                VALUES (?, ?, ?, ?)
                """).params(userId, hash(state), randomValue(), utc(Instant.now().plus(LINK_TTL))).update();
        return UriComponentsBuilder.fromUri(settings.redirectUri().resolve(FinanceOAuthSettings.START))
                .queryParam("ticket", state).build().toUri();
    }

    @Transactional
    public Optional<BrowserAuthorization> begin(String ticket) {
        if (!validRandomValue(ticket)) return Optional.empty();
        String stateHash = hash(ticket);
        String browser = randomValue();
        int claimed = database.sql("""
                UPDATE finance_link_request SET browser_hash = ?
                WHERE state_hash = ? AND browser_hash IS NULL AND expires_at > ?
                """).params(hash(browser), stateHash, utc(Instant.now())).update();
        if (claimed == 0) return Optional.empty();
        String verifier = database.sql("SELECT verifier FROM finance_link_request WHERE state_hash = ?")
                .param(stateHash).query(String.class).single();
        return Optional.of(new BrowserAuthorization(oauth.authorizationUri(ticket, verifier), browser));
    }

    @Transactional
    public LinkResult complete(String state, String browser, String code, String error) {
        if (!validRandomValue(state) || !validRandomValue(browser)) return LinkResult.INVALID;
        String stateHash = hash(state);
        String browserHash = hash(browser);
        var request = database.sql("""
                SELECT user_id, verifier FROM finance_link_request WHERE state_hash = ? AND browser_hash = ?
                """).params(stateHash, browserHash)
                .query((rs, row) -> new LinkRequest(rs.getObject("user_id", UUID.class), rs.getString("verifier")))
                .optional();
        if (request.isEmpty()) return LinkResult.INVALID;
        var pending = request.get();
        lockUser(pending.userId());
        int consumed = database.sql("""
                DELETE FROM finance_link_request
                WHERE state_hash = ? AND browser_hash = ? AND expires_at > ?
                """).params(stateHash, browserHash, utc(Instant.now())).update();
        if (consumed == 0) return LinkResult.INVALID;
        if (error != null || code == null || code.isBlank() || code.length() > 4096) return LinkResult.INVALID;
        try {
            save(pending.userId(), oauth.exchange(code, pending.verifier()));
            return LinkResult.LINKED;
        } catch (InvalidAuthorization rejected) {
            return LinkResult.INVALID;
        } catch (OAuthUnavailable unavailable) {
            return LinkResult.UNAVAILABLE;
        }
    }

    @Transactional
    public Optional<String> accessToken(UUID userId, String rejectedToken) {
        lockUser(userId);
        var stored = database.sql("""
                SELECT issuer, subject, access_token, refresh_token, expires_at FROM finance_link WHERE user_id = ?
                """).param(userId).query((rs, row) -> new StoredTokens(rs.getString("issuer"),
                        rs.getString("subject"), rs.getString("access_token"), rs.getString("refresh_token"),
                        rs.getObject("expires_at", OffsetDateTime.class).toInstant())).optional();
        if (stored.isEmpty() || !settings.issuer().equals(stored.get().issuer())) return Optional.empty();
        var tokens = stored.get();
        String access = encryption.decrypt(userId, "access", tokens.access());
        if (!access.equals(rejectedToken) && Instant.now().plusSeconds(30).isBefore(tokens.expiresAt())) {
            return Optional.of(access);
        }
        try {
            var renewed = oauth.refresh(tokens.subject(), encryption.decrypt(userId, "refresh", tokens.refresh()));
            save(userId, renewed);
            return Optional.of(renewed.access());
        } catch (InvalidAuthorization rejected) {
            database.sql("DELETE FROM finance_link WHERE user_id = ?").param(userId).update();
            return Optional.empty();
        }
    }

    private void save(UUID userId, Tokens tokens) {
        String access = encryption.encrypt(userId, "access", tokens.access());
        String refresh = encryption.encrypt(userId, "refresh", tokens.refresh());
        var expiry = utc(tokens.expiresAt());
        int updated = database.sql("""
                UPDATE finance_link SET issuer = ?, subject = ?, access_token = ?, refresh_token = ?, expires_at = ?
                WHERE user_id = ?
                """).params(settings.issuer(), tokens.subject(), access, refresh, expiry, userId).update();
        if (updated == 0) {
            database.sql("""
                    INSERT INTO finance_link (issuer, subject, access_token, refresh_token, expires_at, user_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """).params(settings.issuer(), tokens.subject(), access, refresh, expiry, userId).update();
        }
    }

    private void lockUser(UUID userId) {
        database.sql("SELECT id FROM app_user WHERE id = ? FOR UPDATE").param(userId).query(UUID.class).single();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static boolean validRandomValue(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{43}");
    }

    private static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            return URL_ENCODER.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record BrowserAuthorization(URI uri, String browserSecret) {}
    public enum LinkResult { LINKED, INVALID, UNAVAILABLE }
    private record LinkRequest(UUID userId, String verifier) {}
    private record StoredTokens(String issuer, String subject, String access, String refresh, Instant expiresAt) {}
}
