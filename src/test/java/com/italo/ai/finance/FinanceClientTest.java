package com.italo.ai.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.italo.ai.finance.FinanceModels.PayExpenseRequest;
import com.italo.ai.finance.FinanceModels.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class FinanceClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://finance.example");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final FinanceClient client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
            .build().createClient(FinanceClient.class);

    @Test
    void sendsIsoDatesAndDeserializesExpenseReport() {
        server.expect(requestTo("https://finance.example/expenses?beginDate=2026-11-01&endDate=2026-11-30"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                          "expenses": [{
                            "id": "11111111-1111-1111-1111-111111111111",
                            "description": "Restaurante",
                            "amount": 50.25,
                            "expenseDate": "2026-11-05",
                            "competence": "2026-11",
                            "category": "RESTAURANT",
                            "accountId": "22222222-2222-2222-2222-222222222222",
                            "canReverse": false,
                            "paymentStatus": "PENDING",
                            "canPay": true,
                            "totalPaid": 0
                          }],
                          "installmentPlans": []
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.listExpenses("Bearer test-token", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30));

        assertThat(response.getBody().expenses()).singleElement().satisfies(expense -> {
            assertThat(expense.amount()).isEqualByComparingTo(new BigDecimal("50.25"));
            assertThat(expense.competence()).isEqualTo("2026-11");
            assertThat(expense.canPay()).isTrue();
            assertThat(expense.payment()).isNull();
        });
        assertThat(response.getBody().installmentPlans()).isEmpty();
        server.verify();
    }

    @Test
    void sendsEachAccountIdAsAQueryValue() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        server.expect(requestTo("https://finance.example/expenses/by-competence?accountId=" + first
                        + "&accountId=" + second + "&month=11&year=2026"))
                .andRespond(withSuccess("""
                        {"installmentExpenses":[],"singleExpenses":[],"totalInstallments":0,"totalSingle":0}
                        """, MediaType.APPLICATION_JSON));

        client.listExpensesByCompetence("Bearer test-token", List.of(first, second), 11, 2026);

        server.verify();
    }

    @Test
    void serializesPaymentRequestAndAcceptsAnEmptyResponse() {
        var expenseId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        server.expect(requestTo("https://finance.example/expenses/" + expenseId + "/pay"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"paidFromAccountId":"%s","paymentDate":"2026-11-05","paymentMethod":"PIX"}
                        """.formatted(accountId)))
                .andRespond(withNoContent());

        var response = client.payExpense("Bearer test-token", expenseId,
                new PayExpenseRequest(accountId, LocalDate.of(2026, 11, 5), PaymentMethod.PIX));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        server.verify();
    }
}
