package com.italo.ai.finance;

import com.italo.ai.finance.FinanceModels.AccountDto;
import com.italo.ai.finance.FinanceModels.AccountMovementDto;
import com.italo.ai.finance.FinanceModels.AccountType;
import com.italo.ai.finance.FinanceModels.CreateAccountCommand;
import com.italo.ai.finance.FinanceModels.CreateExpenseCommand;
import com.italo.ai.finance.FinanceModels.ExpenseByDateDto;
import com.italo.ai.finance.FinanceModels.ExpensesByCompetenceDto;
import com.italo.ai.finance.FinanceModels.IncomeDto;
import com.italo.ai.finance.FinanceModels.IncomeSourceDto;
import com.italo.ai.finance.FinanceModels.InvoiceView;
import com.italo.ai.finance.FinanceModels.SaveIncomeCommand;
import com.italo.ai.finance.FinanceModels.SaveIncomeSourceCommand;
import java.text.Normalizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Tools vinculadas pelo backend ao usuário de uma única mensagem. */
public final class FinanceTools {
    private static final Logger log = LoggerFactory.getLogger(FinanceTools.class);
    private final UUID userId;
    private final FinanceLinks links;
    private final FinanceClient client;
    private volatile boolean needsLogin;
    private volatile String creationStatus;
    private volatile String creationFailure;
    private String currentMessage;

    FinanceTools(UUID userId, FinanceLinks links, FinanceClient client) {
        this.userId = userId;
        this.links = links;
        this.client = client;
    }

    public boolean needsLogin() {
        return needsLogin;
    }

    public void useCurrentMessage(String message) {
        currentMessage = accountWords(message);
    }

    public String creationFailure() {
        return creationFailure;
    }

    public String creationStatus() {
        return creationStatus;
    }

    @Tool(description = "Lista contas do usuário e seus saldos, tipos e IDs. Use para perguntas sobre saldo "
            + "e para descobrir o ID de uma conta pelo nome.")
    public Result<List<AccountDto>> consultarContas(
            @ToolParam(description = "Nome da conta como o usuário informou, incluindo conta corrente ou cartão se indicado. Omita para listar todas.", required = false)
            String nome) {
        var result = read(auth -> client.listAccounts(auth).getBody());
        if (result.dados() == null || nome == null || nome.isBlank()) return result;
        String query = accountWords(nome);
        String alias = accountAlias(nome);
        var type = requestedAccountType(query);
        var named = result.dados().stream()
                .filter(account -> type == null || account.accountType() == type)
                .toList();
        if (type != null && alias.isEmpty() && !named.isEmpty()) {
            return accountMatches(named.stream().filter(AccountDto::active).toList());
        }
        var exact = named.stream().filter(account -> accountWords(account.name()).equals(query)).toList();
        if (!exact.isEmpty()) return accountMatches(exact);
        var equivalent = named.stream()
                .filter(account -> !alias.isEmpty() && accountAlias(account.name()).equals(alias)).toList();
        if (!equivalent.isEmpty()) return accountMatches(equivalent);
        var similar = named.stream()
                .filter(account -> List.of(alias.split(" ")).stream()
                        .anyMatch(word -> word.length() >= 3 && containsWords(accountAlias(account.name()), word)))
                .toList();
        return similar.isEmpty()
                ? new Result<>("Nenhuma conta semelhante encontrada.", List.of())
                : new Result<>("Possíveis contas semelhantes. Peça confirmação antes de registrar.", similar);
    }

    private static Result<List<AccountDto>> accountMatches(List<AccountDto> accounts) {
        if (accounts.isEmpty()) return new Result<>("Nenhuma conta semelhante encontrada.", List.of());
        return new Result<>(accounts.size() == 1 ? "Conta encontrada."
                : "Mais de uma conta corresponde. Peça confirmação antes de registrar.", accounts);
    }

    private static AccountType requestedAccountType(String words) {
        if (containsWords(words, "cartao")) return AccountType.CREDIT_CARD;
        if (containsWords(words, "conta corrente")) return AccountType.CHECKING_ACCOUNT;
        return null;
    }

    private static String accountAlias(String name) {
        return accountWords(name).replaceFirst("^(cartao( de credito)?|conta corrente)( |$)", "")
                .replaceFirst("^(do|da|de) ", "");
    }

    private static boolean containsWords(String text, String words) {
        return !words.isEmpty() && (" " + text + " ").contains(" " + words + " ");
    }

    private static String normalizeAccountName(String name) {
        return Normalizer.normalize(name.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    @Tool(description = "Consulta entradas e saídas de uma conta. Descubra o ID da conta com consultarContas.")
    public Result<List<AccountMovementDto>> consultarMovimentacoes(
            @ToolParam(description = "UUID da conta.") UUID contaId) {
        return read(auth -> client.listAccountMovements(auth, contaId).getBody());
    }

    @Tool(description = "Consulta despesas e planos de parcelamento pelo intervalo de datas da compra.")
    public Result<ExpenseByDateDto> consultarDespesasPorData(
            @ToolParam(description = "Data inicial em AAAA-MM-DD.") LocalDate inicio,
            @ToolParam(description = "Data final em AAAA-MM-DD.") LocalDate fim) {
        return read(auth -> client.listExpenses(auth, inicio, fim).getBody());
    }

    @Tool(description = "Consulta despesas avulsas e parcelas por mês de competência, com totais.")
    public Result<ExpensesByCompetenceDto> consultarDespesasPorCompetencia(
            @ToolParam(description = "Mês de competência, de 1 a 12.") int mes,
            @ToolParam(description = "Ano de competência.") int ano,
            @ToolParam(description = "UUIDs das contas a filtrar; omita para todas.", required = false)
            List<UUID> contas) {
        return read(auth -> client.listExpensesByCompetence(auth, contas, mes, ano).getBody());
    }

    @Tool(description = "Consulta a fatura mensal de uma conta de cartão de crédito, com totais e despesas. "
            + "Descubra o ID do cartão com consultarContas.")
    public Result<InvoiceView> consultarFatura(
            @ToolParam(description = "UUID da conta do cartão.") UUID contaId,
            @ToolParam(description = "Mês de competência em AAAA-MM.") String competencia) {
        return read(auth -> client.getInvoice(auth, contaId, competencia));
    }

    @Tool(description = "Consulta as rendas previstas ou recebidas no mês informado.")
    public Result<List<IncomeDto>> consultarRendas(
            @ToolParam(description = "Mês previsto em AAAA-MM.") String mes) {
        return read(auth -> client.listIncomes(auth, mes).getBody());
    }

    @Tool(description = "Lista as fontes de renda e suas estimativas cadastradas no Finance.")
    public Result<List<IncomeSourceDto>> consultarFontesDeRenda() {
        return read(auth -> client.listIncomeSources(auth).getBody());
    }

    @Tool(description = "Cria uma conta corrente a pedido do usuário. Use quando houver nome e saldo inicial, "
            + "inclusive saldo zero. Os únicos argumentos são nome e saldoInicial.")
    public Result<Boolean> criarContaCorrente(
            @ToolParam(description = "Nome da conta corrente informado pelo usuário.") String nome,
            @ToolParam(description = "Saldo inicial informado pelo usuário, inclusive zero.") BigDecimal saldoInicial) {
        return criarConta(nome, AccountType.CHECKING_ACCOUNT, null, null, saldoInicial);
    }

    @Tool(description = "Cria uma conta a pedido do usuário. Tipos: CHECKING_ACCOUNT (conta corrente, "
            + "use criarContaCorrente), CREDIT_CARD (cartão, exige dias de fechamento e vencimento), "
            + "FIXED_BILL (conta fixa, exige dia de vencimento). Informe saldo inicial somente para conta corrente.")
    public Result<Boolean> criarConta(
            @ToolParam(description = "Nome da conta ou do cartão informado pelo usuário.") String nome,
            @ToolParam(description = "Tipo da conta: CHECKING_ACCOUNT, CREDIT_CARD ou FIXED_BILL.") AccountType tipo,
            @ToolParam(description = "Dia de fechamento do cartão, de 1 a 31.", required = false) Integer diaFechamento,
            @ToolParam(description = "Dia de vencimento do cartão ou da conta fixa, de 1 a 31.", required = false)
            Integer diaVencimento,
            @ToolParam(description = "Saldo inicial informado para conta corrente.", required = false)
            BigDecimal saldoInicial) {
        if (nome == null || nome.isBlank() || tipo == null) {
            return new Result<>("Informe o nome e o tipo da conta.", null);
        }
        String missing = switch (tipo) {
            case CREDIT_CARD -> diaFechamento == null || diaVencimento == null
                    ? "Informe os dias de fechamento e vencimento do cartão." : null;
            case FIXED_BILL -> diaVencimento == null ? "Informe o dia de vencimento da conta fixa." : null;
            case CHECKING_ACCOUNT -> saldoInicial == null ? "Informe o saldo inicial da conta corrente." : null;
        };
        if (missing != null) return new Result<>(missing, null);

        var conta = switch (tipo) {
            case CHECKING_ACCOUNT -> new CreateAccountCommand(nome, tipo, null, null, saldoInicial);
            case CREDIT_CARD -> new CreateAccountCommand(nome, tipo, diaFechamento, diaVencimento, null);
            case FIXED_BILL -> new CreateAccountCommand(nome, tipo, null, diaVencimento, null);
        };
        return create("criarConta", auth -> client.createAccount(auth, conta), "Conta criada.");
    }

    @Tool(description = "Registra uma compra, inclusive parcelada, a pedido do usuário. "
            + "Use esta ferramenta para compras no cartão, inclusive quando o usuário disser que pagou com cartão. "
            + "Resolva accountId com consultarContas e envie o valor total e o número de parcelas informado.")
    public Result<Boolean> criarCompra(
            @ToolParam(description = "Descrição da compra.") String description,
            @ToolParam(description = "Valor total da compra.") BigDecimal amount,
            @ToolParam(description = "Data da compra em AAAA-MM-DD.") LocalDate eventDate,
            @ToolParam(description = "Código de categoria do esquema. Use OTHER quando nenhuma se encaixar.")
            FinanceModels.ExpenseCategory category,
            @ToolParam(description = "UUID da conta confirmada.") UUID accountId,
            @ToolParam(description = "Número de parcelas informado; omita se não houver parcelamento.", required = false)
            Integer installments) {
        return criarDespesa(new CreateExpenseCommand(
                description, amount, eventDate, category, accountId, installments, null, null, null));
    }

    @Tool(description = "Registra uma despesa já paga a pedido do usuário, por exemplo via PIX. Para uma compra no cartão ou parcelada, use criarCompra. Resolva o UUID da conta com consultarContas. "
            + "Preencha description, amount (valor total), eventDate (data em AAAA-MM-DD), category e accountId. Use o código da categoria do esquema, ou OTHER se nenhuma se encaixar. Omita installments para uma parcela. "
            + "Informe paymentMethod e paymentDate só para registrar uma despesa já paga em conta corrente, "
            + "pois isso também debita a conta; use o UUID de uma conta corrente ativa e installments igual a 1.")
    public Result<Boolean> criarDespesa(CreateExpenseCommand despesa) {
        String invalid = validateExpense(despesa);
        if (invalid != null) return new Result<>(invalid, null);
        String accountProblem = validateExpenseAccounts(List.of(despesa));
        if (accountProblem != null) {
            creationStatus = accountProblem;
            return new Result<>(accountProblem, null);
        }
        return create("criarDespesa", auth -> client.createExpense(auth, despesa), "Despesa criada.");
    }

    @Tool(description = "Registra várias despesas quando o usuário pede explicitamente para cadastrar todas. "
            + "Cada despesa segue as regras de criarDespesa. Não repita o lote automaticamente em caso de erro.")
    public Result<Boolean> criarDespesasEmLote(List<CreateExpenseCommand> despesas) {
        if (despesas == null || despesas.isEmpty()) return new Result<>("Informe pelo menos uma despesa.", null);
        for (int index = 0; index < despesas.size(); index++) {
            String invalid = validateExpense(despesas.get(index));
            if (invalid != null) return new Result<>("Despesa " + (index + 1) + ": " + invalid, null);
        }
        String accountProblem = validateExpenseAccounts(despesas);
        if (accountProblem != null) {
            creationStatus = accountProblem;
            return new Result<>(accountProblem, null);
        }
        var result = create("criarDespesasEmLote", auth -> client.createExpenses(auth, despesas), "Despesas criadas.");
        if (result.dados() == null && !needsLogin) {
            return new Result<>(result.status()
                    + " Parte do lote pode ter sido salva. Consulte as despesas antes de tentar novamente.", null);
        }
        return result;
    }

    private String validateExpenseAccounts(List<CreateExpenseCommand> expenses) {
        if (currentMessage == null) {
            return null;
        }
        var accounts = consultarContas(null);
        if (accounts.dados() == null) return accounts.status();

        for (var expense : expenses) {
            var account = accounts.dados().stream()
                    .filter(candidate -> expense.accountId().equals(candidate.accountId()))
                    .findFirst().orElse(null);
            if (account == null || currentMessage != null && !mentionsAccount(account, accounts.dados())) {
                return "Qual conta você usou? Informe o nome da conta para registrar a despesa.";
            }

        }
        return null;
    }

    private boolean mentionsAccount(AccountDto account, List<AccountDto> accounts) {
        var type = requestedAccountType(currentMessage);
        if (type != null && account.accountType() != type) return false;
        String alias = accountAlias(account.name());
        if (containsWords(currentMessage, accountWords(account.name()))
                && !accountWords(account.name()).equals(alias)) return true;
        if (!containsWords(currentMessage, alias)) {
            return type != null && account.active()
                    && accounts.stream().filter(candidate -> candidate.active() && candidate.accountType() == type)
                            .count() == 1
                    && accounts.stream().noneMatch(candidate -> containsWords(currentMessage, accountAlias(candidate.name())));
        }
        return accounts.stream().filter(candidate -> type == null || candidate.accountType() == type)
                .filter(candidate -> accountAlias(candidate.name()).equals(alias)).count() == 1;
    }

    private static String accountWords(String text) {
        return normalizeAccountName(text).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static String validateExpense(CreateExpenseCommand expense) {
        if (expense == null) return "Informe os dados da despesa.";
        if (expense.description() == null || expense.description().isBlank()) {
            return "Preencha description com a descrição da despesa.";
        }
        if (expense.amount() == null || expense.amount().signum() <= 0) {
            return "Preencha amount com o valor total positivo da despesa.";
        }
        if (expense.eventDate() == null) return "Preencha eventDate com a data da despesa em AAAA-MM-DD.";
        if (expense.category() == null) return "Preencha category com um código do esquema, ou OTHER.";
        if (expense.accountId() == null) return "Resolva accountId consultando as contas pelo nome.";
        if ((expense.paymentMethod() == null) != (expense.paymentDate() == null)) {
            return "Para despesa já paga, preencha paymentMethod e paymentDate juntos.";
        }
        return null;
    }

    @Tool(description = "Cria uma fonte de renda a pedido do usuário. Informe nome, tipo, dia previsto (1 a 31) "
            + "e valor estimado. A descrição é opcional.")
    public Result<Boolean> criarFonteDeRenda(SaveIncomeSourceCommand fonte) {
        return create("criarFonteDeRenda", auth -> client.createIncomeSource(auth, fonte), "Fonte de renda criada.");
    }

    @Tool(description = "Cadastra uma renda pendente a pedido do usuário. Informe valor, data prevista, "
            + "competência em AAAA-MM, UUID da fonte (consulte consultarFontesDeRenda) "
            + "e UUID da conta corrente de destino (consulte consultarContas).")
    public Result<Boolean> criarRenda(SaveIncomeCommand renda) {
        return create("criarRenda", auth -> client.createIncome(auth, renda), "Renda criada.");
    }

    private <T> Result<T> read(Function<String, T> operation) {
        return execute(auth -> new Result<>("Consulta concluída.", operation.apply(auth)), null);
    }

    private Result<Boolean> create(String tool, Function<String, ResponseEntity<Void>> operation, String success) {
        var result = this.<Boolean>execute(auth -> {
            int status = operation.apply(auth).getStatusCode().value();
            if (status == HttpStatus.CREATED.value()) return new Result<>(success, true);
            log.warn("Finance retornou status inesperado para {}: HTTP {}", tool, status);
            return unconfirmedCreation();
        }, tool);
        creationStatus = result.status();
        creationFailure = result.dados() == null ? result.status() : null;
        return result;
    }

    private <T> Result<T> execute(Function<String, Result<T>> operation, String creationTool) {
        boolean creation = creationTool != null;
        String tool = creation ? creationTool : "consulta";
        if (needsLogin) return loginRequired();
        try {
            var access = links.accessToken(userId, null);
            if (access.isEmpty()) return loginRequired();
            String token = access.get();
            try {
                return operation.apply("Bearer " + token);
            } catch (RestClientResponseException rejected) {
                if (rejected.getStatusCode().value() != 401) throw rejected;
                var renewed = links.accessToken(userId, token);
                if (renewed.isEmpty()) return loginRequired();
                return operation.apply("Bearer " + renewed.get());
            }
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            var problem = financeProblem(failure);
            String code = problem == null ? null : problem.code();
            String diagnostic = "HTTP " + status + (code == null ? "" : ", " + code);
            log.warn("Falha no Finance em {}: {}", tool, diagnostic);
            Result<T> result = switch (status) {
                case 401 -> loginRequired();
                case 400, 422 -> new Result<>(problem != null && problem.message() != null
                        ? problem.message() : "O Finance rejeitou os dados informados.", null);
                case 403 -> new Result<>("O usuário não tem permissão para essa operação no Finance.", null);
                case 404 -> new Result<>("O registro solicitado não foi encontrado no Finance.", null);
                case 409 -> new Result<>("O Finance recusou a operação por um conflito com dados existentes.", null);
                default -> creation ? unconfirmedCreation() : new Result<>("Finance indisponível no momento.", null);
            };
            return result;
        } catch (FinanceOAuth.OAuthUnavailable unavailable) {
            log.warn("Falha ao obter autorização do Finance em {}", tool);
            return new Result<>("Não foi possível obter a autorização do Finance no momento.", null);
        } catch (RestClientException unavailable) {
            log.warn("Falha no cliente HTTP do Finance em {}: {}", tool, unavailable.getClass().getSimpleName());
            return creation ? unconfirmedCreation() : new Result<>("Finance indisponível no momento.", null);
        }
    }

    private FinanceProblem financeProblem(RestClientResponseException failure) {
        try {
            var body = JsonMapper.shared().readTree(failure.getResponseBodyAsByteArray());
            String code = body.path("code").asString("");
            if (!code.matches("[A-Z][A-Z0-9_]{0,79}")) return null;
            String message = body.path("message").asString("").strip();
            return new FinanceProblem(code, message.isEmpty() || message.length() > 300 ? null : message);
        } catch (JacksonException malformed) {
            return null;
        }
    }

    private record FinanceProblem(String code, String message) {}

    private <T> Result<T> loginRequired() {
        needsLogin = true;
        return new Result<>("A autorização do usuário é necessária. O aplicativo enviará o link de login.", null);
    }

    private <T> Result<T> unconfirmedCreation() {
        return new Result<>("O Finance não confirmou a criação. Consulte os registros antes de tentar novamente.", null);
    }

    public record Result<T>(String status, T dados) {}
}
