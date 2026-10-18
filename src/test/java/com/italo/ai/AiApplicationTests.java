package com.italo.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class AiApplicationTests {

	@Test
	void contextLoads() {
	}

}
