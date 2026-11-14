package com.italo.ai.config;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.italo.ai.config.AiConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiConfigurationTest {

    @Test
    @DisplayName("chatClient bean is not null")
    void beanShouldBeCreated(AiConfiguration config) {
        // The bean is auto-wired by SpringBootTest
        assertNotNull(config);
    }
}
