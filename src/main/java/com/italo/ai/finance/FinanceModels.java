package com.italo.ai.finance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Modelos de entrada e saída da API Finance. */
public final class FinanceModels {
    private FinanceModels() {}

    public enum AccountType {
        CREDIT_CARD, CHECKING_ACCOUNT, FIXED_BILL
    }

    public enum ExpenseCategory {
        GROCERIES, HOUSING, TRANSPORT, FUEL, SUBSCRIPTION, LEISURE, RESTAURANT,
        EDUCATION, ELECTRONICS, HEALTH, MOTORCYCLE, SUPERMARKET, OTHER
    }

    public enum PaymentMethod {
        PIX, DEBIT, CASH, TRANSFER
    }

    public enum IncomeSourceType {
        SALARY, SERVICES, RENT, BENEFIT_OR_PENSION, INVESTMENT_RETURNS, OTHER
    }

    public record CreateAccountCommand(
            String name, AccountType accountType,
            @JsonProperty(required = false) Integer closingDay, @JsonProperty(required = false) Integer dueDay,
            @JsonProperty(required = false) BigDecimal initialBalance) {}

    public record UpdateAccountCommand(
            String name, AccountType accountType, Integer closingDay, Integer dueDay) {}

    public record CreateExpenseCommand(
            String description, BigDecimal amount, LocalDate eventDate, ExpenseCategory category,
            UUID accountId,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty(required = false) Integer installments,
            @JsonProperty(required = false) PaymentMethod paymentMethod,
            @JsonProperty(required = false) LocalDate paymentDate,
            @JsonProperty(required = false) UUID paidFromAccountId) {
        public CreateExpenseCommand {
            if (paymentMethod != null && installments == null) installments = 1;
        }
    }

    public record PayExpenseRequest(
            UUID paidFromAccountId, LocalDate paymentDate, PaymentMethod paymentMethod) {}

    public record SaveIncomeSourceCommand(
            String name, IncomeSourceType type, @JsonProperty(required = false) String description, int expectedDay,
            BigDecimal estimatedAmount) {}

    public record SaveIncomeCommand(
            UUID incomeSourceId, BigDecimal amount, LocalDate expectedDate, UUID accountId,
            String competence) {}

    public record ReceiveIncomeRequest(LocalDate receivedDate) {}

    public record ExpenseComparison(BigDecimal currentTotal, BigDecimal differenceFromPrevious) {}

    public record ExpenseView(String description, LocalDate expenseDate, BigDecimal amount) {}

    public record InvoiceView(
            String competence, LocalDate dueDate, ExpenseComparison total,
            ExpenseComparison totalInstallments, ExpenseComparison totalSingle,
            List<ExpenseView> installmentsExpenses, List<ExpenseView> singleExpenses,
            String creditCardName, Map<ExpenseCategory, ExpenseComparison> details) {}

    public record AccountDto(
            String name, AccountType accountType, UUID accountId, boolean active, BigDecimal balance) {}

    public enum MovementOrigin {
        OPENING, RECEIPT, PAYMENT, REVERSAL
    }

    public record AccountMovementDto(
            UUID id, UUID accountId, BigDecimal amount, LocalDate date, MovementOrigin origin,
            UUID originId, String description) {}

    public record ExpensePaymentSummary(
            UUID id, BigDecimal amount, LocalDate paymentDate, PaymentMethod paymentMethod,
            UUID paidFromAccountId, UUID movementId) {}

    public record ExpenseItemDto(
            UUID id, String description, BigDecimal amount, LocalDate expenseDate, String competence,
            ExpenseCategory category, UUID accountId, UUID paymentMovementId, LocalDate reversedDate,
            boolean canReverse, String paymentStatus, boolean canPay, ExpensePaymentSummary payment,
            BigDecimal totalPaid) {}

    public record InstallmentPlanItemDto(
            UUID id, String description, BigDecimal totalAmount, int totalInstallments,
            LocalDate expenseDate, UUID accountId) {}

    public record ExpenseByDateDto(List<ExpenseItemDto> expenses, List<InstallmentPlanItemDto> installmentPlans) {}

    public record ExpensesByCompetenceDto(
            List<ExpenseItemDto> installmentExpenses, List<ExpenseItemDto> singleExpenses,
            BigDecimal totalInstallments, BigDecimal totalSingle) {}

    public record IncomeSourceDto(
            UUID id, String name, IncomeSourceType type, String description, Integer expectedDay,
            BigDecimal estimatedAmount) {}

    public record IncomeDto(
            UUID id, UUID incomeSourceId, BigDecimal amount, LocalDate expectedDate, UUID accountId,
            LocalDate receivedDate, String status, String competence) {}
}
