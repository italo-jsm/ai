package com.italo.ai.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinanceTokenEncryptionTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void acceptsWhitespaceAroundTheBase64Key() {
        var user = UUID.randomUUID();
        var original = new FinanceTokenEncryption(key);
        var withWhitespace = new FinanceTokenEncryption(" " + key + "\r\n");

        assertThat(withWhitespace.decrypt(user, "refresh", original.encrypt(user, "refresh", "token")))
                .isEqualTo("token");
    }

    @Test
    void identifiesTheVariableWhenTheKeyHasExtraPadding() {
        assertThatThrownBy(() -> new FinanceTokenEncryption(key + "="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FINANCE_OAUTH_ENCRYPTION_KEY")
                .hasNoCause();
    }
}
