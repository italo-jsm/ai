package com.italo.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TestTools {

    private static final Logger log =
            LoggerFactory.getLogger(TestTools.class);

    @Tool(description = """
        Obtém o código secreto de teste.
        Use quando o usuário perguntar qual é o código secreto.
        """)
    public String getSecretCode() {
        log.info(">>> TOOL EXECUTADA: getSecretCode");

        return "JAVA-SPRING-98765";
    }
}
