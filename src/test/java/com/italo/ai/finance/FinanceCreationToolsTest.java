package com.italo.ai.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.italo.ai.finance.FinanceModels.AccountType;
import com.italo.ai.finance.FinanceModels.CreateAccountCommand;
import com.italo.ai.finance.FinanceModels.CreateExpenseCommand;
import com.italo.ai.finance.FinanceModels.ExpenseCategory;
import com.italo.ai.finance.FinanceModels.IncomeSourceType;
import com.italo.ai.finance.FinanceModels.SaveIncomeCommand;
import com.italo.ai.finance.FinanceModels.SaveIncomeSourceCommand;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class FinanceCreationToolsTest {
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String AUTHORIZATION = "Bearer user-token";
    private static final String EXPENSE = """
            {"description":"Mercado","amount":85.50,"eventDate":"2026-11-18","category":"GROCERIES",
             "accountId":"11111111-1111-1111-1111-111111111111"}
            """;

    private final UUID user = UUID.randomUUID();
    private final FinanceLinks links = mock(FinanceLinks.class);
    private final FinanceClient client = mock(FinanceClient.class);
    private final FinanceTools tools = new FinanceTools(user, links, client);

    @Test
    void exposesAllCreationCallbacksAndUsesTheSendersBearerToken() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        var created = ResponseEntity.status(HttpStatus.CREATED).<Void>build();
        when(client.createAccount(eq(AUTHORIZATION), any())).thenReturn(created);
        when(client.createExpense(eq(AUTHORIZATION), any())).thenReturn(created);
        when(client.createExpenses(eq(AUTHORIZATION), any())).thenReturn(created);
        when(client.createIncomeSource(eq(AUTHORIZATION), any())).thenReturn(created);
        when(client.createIncome(eq(AUTHORIZATION), any())).thenReturn(created);

        assertThat(callback("criarConta").call("""
                {"nome":"Minha conta","tipo":"CHECKING_ACCOUNT","saldoInicial":0}
                """)).contains("Conta criada.");
        assertThat(callback("criarDespesa").call("{\"despesa\":" + EXPENSE + "}")).contains("Despesa criada.");
        assertThat(callback("criarDespesasEmLote").call("{\"despesas\":[" + EXPENSE + "]}")).contains("Despesas criadas.");
        assertThat(callback("criarFonteDeRenda").call("""
                {"fonte":{"name":"Trabalho","type":"SERVICES","expectedDay":10,"estimatedAmount":2500}}
                """)).contains("Fonte de renda criada.");
        assertThat(callback("criarRenda").call("""
                {"renda":{"incomeSourceId":"22222222-2222-2222-2222-222222222222","amount":2500,
                          "expectedDate":"2026-11-20","accountId":"11111111-1111-1111-1111-111111111111",
                          "competence":"2026-11"}}
                """)).contains("Renda criada.");

        var expense = new CreateExpenseCommand("Mercado", new BigDecimal("85.5"), LocalDate.of(2026, 11, 18),
                ExpenseCategory.GROCERIES, ACCOUNT, null, null, null, null);
        verify(client).createAccount(AUTHORIZATION,
                new CreateAccountCommand("Minha conta", AccountType.CHECKING_ACCOUNT, null, null, BigDecimal.ZERO));
        verify(client).createExpense(AUTHORIZATION, expense);
        verify(client).createExpenses(AUTHORIZATION, List.of(expense));
        verify(client).createIncomeSource(AUTHORIZATION,
                new SaveIncomeSourceCommand("Trabalho", IncomeSourceType.SERVICES, null, 10, new BigDecimal("2500")));
        verify(client).createIncome(AUTHORIZATION,
                new SaveIncomeCommand(SOURCE, new BigDecimal("2500"), LocalDate.of(2026, 11, 20), ACCOUNT, "2026-11"));
    }

    @Test
    void rejectsMissingAccountArgumentsBeforeMakingAnHttpCall() {
        assertThat(callback("criarConta").call("""
                {"nome":"Cartão Itaú","tipo":"CREDIT_CARD","diaFechamento":21}
                """)).contains("Informe os dias de fechamento e vencimento");
        assertThat(callback("criarConta").call("{}")).contains("Informe o nome e o tipo");

        verifyNoInteractions(client);
    }

    @Test
    void forwardsCreditCardNameClosingAndDueDays() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        when(client.createAccount(eq(AUTHORIZATION), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        assertThat(callback("criarConta").call("""
                {"nome":"Cartão de Crédito Itaú","tipo":"CREDIT_CARD","diaFechamento":21,"diaVencimento":28}
                """)).contains("Conta criada.");

        verify(client).createAccount(AUTHORIZATION,
                new CreateAccountCommand("Cartão de Crédito Itaú", AccountType.CREDIT_CARD, 21, 28, null));
    }

    @Test
    void requestsLoginBeforeCreatingAnything() {
        when(links.accessToken(user, null)).thenReturn(Optional.empty());

        assertThat(callback("criarDespesa").call("{\"despesa\":" + EXPENSE + "}")).contains("autorização");
        assertThat(tools.needsLogin()).isTrue();
        verifyNoInteractions(client);
    }

    @Test
    void reportsHttpStatusAndBusinessCodeWithoutExposingTheErrorBody() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        byte[] body = """
                {"code":"ACCOUNT_DUE_DAY_INVALID","message":"conteúdo interno"}
                """.getBytes(StandardCharsets.UTF_8);
        when(client.createAccount(eq(AUTHORIZATION), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8));

        assertThat(callback("criarConta").call("""
                {"nome":"Cartão","tipo":"CREDIT_CARD","diaFechamento":21,"diaVencimento":32}
                """)).contains("HTTP 400", "ACCOUNT_DUE_DAY_INVALID").doesNotContain("conteúdo interno", "user-token");
    }

    @Test
    void doesNotRetryWhenTheCreationResponseIsLost() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        when(client.createExpense(eq(AUTHORIZATION), any())).thenThrow(new ResourceAccessException("timeout"));

        assertThat(callback("criarDespesa").call("{\"despesa\":" + EXPENSE + "}")).contains("não confirmou");
        verify(client).createExpense(eq(AUTHORIZATION), any());
        verifyNoMoreInteractions(client);
    }

    private ToolCallback callback(String name) {
        for (var callback : ToolCallbacks.from(tools)) {
            if (name.equals(callback.getToolDefinition().name())) return callback;
        }
        throw new AssertionError("Tool ausente: " + name);
    }
}
