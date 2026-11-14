package com.italo.ai.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestToolsTest {

    @Test
    @DisplayName("getSecretCode returns expected")
    void testGetSecretCode() {
        TestTools tools = new TestTools();
        assertEquals("JAVA-SPRING-98765", tools.getSecretCode());
    }
}
