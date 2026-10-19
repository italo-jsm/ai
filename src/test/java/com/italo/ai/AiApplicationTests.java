package com.italo.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=integration-test-key",
    "spring.ai.openai.chat.model=integration-test-model"
})
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class AiApplicationTests {

	@Test
	void contextLoads() {
	}

}
