package com.italo.ai.finance;

import com.italo.ai.finance.FinanceModels.AccountDto;
import com.italo.ai.finance.FinanceModels.AccountMovementDto;
import com.italo.ai.finance.FinanceModels.CreateAccountCommand;
import com.italo.ai.finance.FinanceModels.CreateExpenseCommand;
import com.italo.ai.finance.FinanceModels.ExpenseByDateDto;
import com.italo.ai.finance.FinanceModels.ExpensesByCompetenceDto;
import com.italo.ai.finance.FinanceModels.IncomeDto;
import com.italo.ai.finance.FinanceModels.IncomeSourceDto;
import com.italo.ai.finance.FinanceModels.InvoiceView;
import com.italo.ai.finance.FinanceModels.PayExpenseRequest;
import com.italo.ai.finance.FinanceModels.ReceiveIncomeRequest;
import com.italo.ai.finance.FinanceModels.SaveIncomeCommand;
import com.italo.ai.finance.FinanceModels.SaveIncomeSourceCommand;
import com.italo.ai.finance.FinanceModels.UpdateAccountCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

/** O argumento authorization recebe o cabeçalho completo, no formato Bearer seguido do token. */
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface FinanceClient {
    @GetExchange("/accounts")
    ResponseEntity<List<AccountDto>> listAccounts(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostExchange(url = "/accounts", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> createAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody CreateAccountCommand command);

    @PutExchange(url = "/accounts/{id}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<AccountDto> updateAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id, @RequestBody UpdateAccountCommand command);

    @DeleteExchange("/accounts/{id}")
    ResponseEntity<Void> deleteAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id);

    @PatchExchange("/accounts/{id}/deactivate")
    ResponseEntity<AccountDto> deactivateAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id);

    @PatchExchange("/accounts/{id}/reactivate")
    ResponseEntity<AccountDto> reactivateAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id);

    @GetExchange("/accounts/{id}/movements")
    ResponseEntity<List<AccountMovementDto>> listAccountMovements(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id);

    @GetExchange("/expenses")
    ResponseEntity<ExpenseByDateDto> listExpenses(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("beginDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beginDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    @GetExchange("/expenses/by-competence")
    ResponseEntity<ExpensesByCompetenceDto> listExpensesByCompetence(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(name = "accountId", required = false) List<UUID> accountIds,
            @RequestParam("month") int month, @RequestParam("year") int year);

    @PostExchange(url = "/expenses", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> createExpense(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody CreateExpenseCommand command);

    @PostExchange(url = "/expenses/many", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> createExpenses(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody List<CreateExpenseCommand> commands);

    @PostExchange(url = "/expenses/{id}/pay", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> payExpense(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id, @RequestBody PayExpenseRequest request);

    @PostExchange("/expenses/{id}/reverse")
    ResponseEntity<Void> reverseExpense(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id);

    @GetExchange("/invoices")
    InvoiceView getInvoice(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("accountId") UUID accountId, @RequestParam("competence") String competence);

    @GetExchange("/income-sources")
    ResponseEntity<List<IncomeSourceDto>> listIncomeSources(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetExchange("/income-sources/{id}")
    ResponseEntity<IncomeSourceDto> getIncomeSource(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id);

    @PostExchange(url = "/income-sources", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> createIncomeSource(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody SaveIncomeSourceCommand command);

    @PutExchange(url = "/income-sources/{id}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<IncomeSourceDto> updateIncomeSource(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id, @RequestBody SaveIncomeSourceCommand command);

    @GetExchange("/incomes")
    ResponseEntity<List<IncomeDto>> listIncomes(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("month") String month);

    @GetExchange("/incomes/{id}")
    ResponseEntity<IncomeDto> getIncome(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id);

    @PostExchange(url = "/incomes", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> createIncome(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody SaveIncomeCommand command);

    @PutExchange(url = "/incomes/{id}", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<IncomeDto> updateIncome(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id, @RequestBody SaveIncomeCommand command);

    @PostExchange(url = "/incomes/{id}/receive", contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<IncomeDto> receiveIncome(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable("id") UUID id, @RequestBody ReceiveIncomeRequest request);
}
