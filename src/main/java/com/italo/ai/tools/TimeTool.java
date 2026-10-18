package com.italo.ai.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TimeTool {

    private static final Logger log =
            LoggerFactory.getLogger(TimeTool.class);

    @Tool(description = """
        Obtém o horario do sistema.
        Use quando precisar obter o horario (data e hora) atual.
        """)
    public LocalDateTime getLocalDateTime() {
        log.info("Buscou horario atual");

        return LocalDateTime.now();
    }
}
