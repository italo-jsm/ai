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

    FinanceTools(UUID userId, FinanceLinks links, FinanceClient client) {
        this.userId = userId;
        this.links = links;
        this.client = client;
    }

    public boolean needsLogin() {
        return needsLogin;
    }

    @Tool(description = "Lista contas do usuário e seus saldos, tipos e IDs. Use para perguntas sobre saldo "
            + "e para descobrir o ID de uma conta pelo nome.")
    public Result<List<AccountDto>> consultarContas(
            @ToolParam(description = "Nome ou parte do nome da conta; omita para listar todas.", required = false)
            String nome) {
        return read(auth -> {
            var accounts = client.listAccounts(auth).getBody();
            if (accounts == null || nome == null || nome.isBlank()) return accounts;
            String filter = nome.strip().toLowerCase(Locale.ROOT);
            return accounts.stream().filter(account -> account.name().toLowerCase(Locale.ROOT).contains(filter)).toList();
        });
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

    @Tool(description = "Cria uma conta a pedido do usuário. Tipos: CHECKING_ACCOUNT (conta corrente, "
            + "exige saldo inicial informado), CREDIT_CARD (cartão, exige dias de fechamento e vencimento), "
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

        var conta = new CreateAccountCommand(nome, tipo, diaFechamento, diaVencimento, saldoInicial);
        return create("criarConta", auth -> client.createAccount(auth, conta), "Conta criada.");
    }

    @Tool(description = "Registra uma despesa a pedido do usuário. Resolva o UUID da conta com consultarContas. "
            + "Informe descrição, valor total, data da compra e categoria. Omita installments para uma parcela. "
            + "Informe paymentMethod e paymentDate só para registrar uma despesa já paga em conta corrente, "
            + "pois isso também debita a conta; nesse caso, informe uma parcela.")
    public Result<Boolean> criarDespesa(CreateExpenseCommand despesa) {
        return create("criarDespesa", auth -> client.createExpense(auth, despesa), "Despesa criada.");
    }

    @Tool(description = "Registra várias despesas quando o usuário pede explicitamente para cadastrar todas. "
            + "Cada despesa segue as regras de criarDespesa. Não repita o lote automaticamente em caso de erro.")
    public Result<Boolean> criarDespesasEmLote(List<CreateExpenseCommand> despesas) {
        if (despesas == null || despesas.isEmpty()) return new Result<>("Informe pelo menos uma despesa.", null);
        var result = create("criarDespesasEmLote", auth -> client.createExpenses(auth, despesas), "Despesas criadas.");
        if (result.dados() == null && !needsLogin) {
            return new Result<>(result.status()
                    + " Parte do lote pode ter sido salva. Consulte as despesas antes de tentar novamente.", null);
        }
        return result;
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
        return execute(auth -> {
            int status = operation.apply(auth).getStatusCode().value();
            if (status == HttpStatus.CREATED.value()) return new Result<>(success, true);
            log.warn("Finance retornou status inesperado para {}: HTTP {}", tool, status);
            return withDiagnostic(unconfirmedCreation(), "HTTP " + status);
        }, tool);
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
            String code = financeErrorCode(failure);
            String diagnostic = "HTTP " + status + (code == null ? "" : ", " + code);
            log.warn("Falha no Finance em {}: {}", tool, diagnostic);
            Result<T> result = switch (status) {
                case 401 -> loginRequired();
                case 400, 422 -> new Result<>("O Finance rejeitou os dados informados.", null);
                case 403 -> new Result<>("O usuário não tem permissão para essa operação no Finance.", null);
                case 404 -> new Result<>("O registro solicitado não foi encontrado no Finance.", null);
                case 409 -> new Result<>("O Finance recusou a operação por um conflito com dados existentes.", null);
                default -> creation ? unconfirmedCreation() : new Result<>("Finance indisponível no momento.", null);
            };
            return withDiagnostic(result, diagnostic);
        } catch (FinanceOAuth.OAuthUnavailable unavailable) {
            log.warn("Falha ao obter autorização do Finance em {}", tool);
            return new Result<>("Não foi possível obter a autorização do Finance no momento.", null);
        } catch (RestClientException unavailable) {
            log.warn("Falha no cliente HTTP do Finance em {}: {}", tool, unavailable.getClass().getSimpleName());
            Result<T> result = creation ? unconfirmedCreation() : new Result<>("Finance indisponível no momento.", null);
            return withDiagnostic(result, "sem resposta HTTP utilizável");
        }
    }

    private String financeErrorCode(RestClientResponseException failure) {
        try {
            var node = JsonMapper.shared().readTree(failure.getResponseBodyAsByteArray()).path("code");
            if (!node.isString()) return null;
            String code = node.asString();
            return code.matches("[A-Z][A-Z0-9_]{0,79}") ? code : null;
        } catch (JacksonException malformed) {
            return null;
        }
    }

    private <T> Result<T> withDiagnostic(Result<T> result, String diagnostic) {
        return new Result<>(result.status() + " Diagnóstico: " + diagnostic + ".", result.dados());
    }

    private <T> Result<T> loginRequired() {
        needsLogin = true;
        return new Result<>("A autorização do usuário é necessária. O aplicativo enviará o link de login.", null);
    }

    private <T> Result<T> unconfirmedCreation() {
        return new Result<>("O Finance não confirmou a criação. Consulte os registros antes de tentar novamente.", null);
    }

    public record Result<T>(String status, T dados) {}
}
