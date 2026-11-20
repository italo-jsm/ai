package com.italo.ai.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void createsCheckingAccountWithOnlyNameAndZeroBalance() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        when(client.createAccount(eq(AUTHORIZATION), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        assertThat(callback("criarContaCorrente").call("""
                {"nome":"Itaú","saldoInicial":0}
                """)).contains("Conta criada.");
        verify(client).createAccount(AUTHORIZATION,
                new CreateAccountCommand("Itaú", AccountType.CHECKING_ACCOUNT, null, null, BigDecimal.ZERO));
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
    void sendsOneInstallmentForCompletedPix() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        when(client.listAccounts(AUTHORIZATION)).thenReturn(ResponseEntity.ok(List.of(
                new FinanceModels.AccountDto("Itau", AccountType.CHECKING_ACCOUNT, ACCOUNT, true, BigDecimal.ZERO))));
        when(client.createExpense(eq(AUTHORIZATION), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        String input = """
                {"despesa":{"description":"Faxina","amount":180,"eventDate":"2026-11-18","category":"OTHER",
                    "accountId":"%s","paymentMethod":"PIX","paymentDate":"2026-11-18"}}
                """.formatted(ACCOUNT);
        tools.useCurrentMessage("Fiz um PIX de 180 reais para pagar a faxineira hoje.");
        assertThat(callback("criarDespesa").call(input)).contains("Qual conta você usou?");
        verify(client, never()).createExpense(any(), any());

        tools.useCurrentMessage("Usei o Itaú.");
        assertThat(callback("criarDespesa").call(input)).contains("Despesa criada.");
        verify(client).createExpense(AUTHORIZATION, new CreateExpenseCommand("Faxina", new BigDecimal("180"),
                LocalDate.of(2026, 11, 18), ExpenseCategory.OTHER, ACCOUNT, 1,
                FinanceModels.PaymentMethod.PIX, LocalDate.of(2026, 11, 18), null));
    }

    @Test
    void retriesTheSamePurchaseOnTheCardAfterFinanceRejectsTheCheckingAccount() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        when(client.listAccounts(AUTHORIZATION)).thenReturn(ResponseEntity.ok(List.of(
                new FinanceModels.AccountDto("Itaú", AccountType.CHECKING_ACCOUNT, ACCOUNT, true, BigDecimal.ZERO),
                new FinanceModels.AccountDto("Itaú", AccountType.CREDIT_CARD, SOURCE, true, null))));
        String reason = "Compras parceladas só podem ser registradas em cartão de crédito.";
        byte[] body = ("{\"code\":\"INSTALLMENT_ACCOUNT_TYPE_INVALID\",\"message\":\"" + reason + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        when(client.createExpense(eq(AUTHORIZATION), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        var purchase = callback("criarCompra");
        assertThat(purchase.getToolDefinition().inputSchema()).doesNotContain("paymentMethod", "paymentDate");
        String input = """
                {"description":"Máquina de lavar","amount":2200,"eventDate":"2026-11-19",
                    "category":"OTHER","accountId":"%s","installments":21}
                """;
        tools.useCurrentMessage("Conta corrente do Itau");
        assertThat(tools.consultarContas("Conta corrente do Itau").dados()).singleElement()
                .satisfies(account -> assertThat(account.accountId()).isEqualTo(ACCOUNT));
        assertThat(purchase.call(input.formatted(ACCOUNT)))
                .contains(reason).doesNotContain("HTTP 400", "INSTALLMENT_ACCOUNT_TYPE_INVALID");

        tools.useCurrentMessage("21 parcelas no cartao de credito");
        assertThat(tools.consultarContas("cartao de credito").dados()).singleElement()
                .satisfies(account -> assertThat(account.accountId()).isEqualTo(SOURCE));
        assertThat(purchase.call(input.formatted(SOURCE))).contains("Despesa criada.");

        for (UUID accountId : List.of(ACCOUNT, SOURCE)) {
            verify(client).createExpense(AUTHORIZATION, new CreateExpenseCommand("Máquina de lavar",
                    new BigDecimal("2200"), LocalDate.of(2026, 11, 19), ExpenseCategory.OTHER,
                    accountId, 21, null, null, null));
        }
    }

    @Test
    void blocksIncompleteExpenseBeforeCallingFinance() {
        assertThat(callback("criarDespesa").call("""
                {"despesa":{"description":"Faxina","category":"OTHER"}}
                """)).contains("amount");
        assertThat(callback("criarDespesasEmLote").call("""
                {"despesas":[{"description":"Faxina","amount":180,"category":"OTHER"}]}
                """)).contains("eventDate");
        verifyNoInteractions(client);
    }

    @Test
    void requestsLoginBeforeCreatingAnything() {
        when(links.accessToken(user, null)).thenReturn(Optional.empty());

        assertThat(callback("criarDespesa").call("{\"despesa\":" + EXPENSE + "}")).contains("autorização");
        assertThat(tools.needsLogin()).isTrue();
        verifyNoInteractions(client);
    }

    @Test
    void reportsTheFinanceBusinessReasonWithoutExposingOtherFields() {
        when(links.accessToken(user, null)).thenReturn(Optional.of("user-token"));
        byte[] body = """
                {"code":"ACCOUNT_DUE_DAY_INVALID","message":"Dia de vencimento inválido.","detail":"conteúdo interno"}
                """.getBytes(StandardCharsets.UTF_8);
        when(client.createAccount(eq(AUTHORIZATION), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, body, StandardCharsets.UTF_8));

        assertThat(callback("criarConta").call("""
                {"nome":"Cartão","tipo":"CREDIT_CARD","diaFechamento":21,"diaVencimento":32}
                """)).contains("Dia de vencimento inválido.")
                .doesNotContain("HTTP 400", "ACCOUNT_DUE_DAY_INVALID", "conteúdo interno", "user-token");
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
