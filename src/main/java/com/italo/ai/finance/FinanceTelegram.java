package com.italo.ai.finance;

import com.italo.ai.user.UserEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.finance.oauth.enabled", havingValue = "true")
public class FinanceTelegram {
    private final FinanceLinks links;
    private final FinanceClient client;

    public FinanceTelegram(FinanceLinks links, FinanceClient client) {
        this.links = links;
        this.client = client;
    }

    public FinanceTools toolsFor(UserEntity user) {
        return new FinanceTools(user.getId(), links, client);
    }

    public String loginMessage(UserEntity user) {
        return "Para acessar o Finance, entre com sua conta do Keycloak pelo link abaixo "
                + "(válido por 10 minutos). Depois, volte ao Telegram e repita a pergunta.\n"
                + links.createLink(user.getId());
    }
}
