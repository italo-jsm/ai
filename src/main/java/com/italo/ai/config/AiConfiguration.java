package com.italo.ai.config;

import com.italo.ai.tools.TestTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {

    @Bean
    ChatClient chatClient(
            ChatClient.Builder builder,
            TestTools testTools) {

        return builder
                .defaultSystem("""
                    Você é um assistente pessoal.
                    Responda em português brasileiro.
                    Utilize ferramentas quando necessário.
                    """)
                .defaultTools(testTools)
                .build();
    }
}
