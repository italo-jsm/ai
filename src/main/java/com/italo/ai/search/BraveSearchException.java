package com.italo.ai.search;

public class BraveSearchException extends RuntimeException {
    public enum Reason {
        CONFIGURATION, AUTHENTICATION, RATE_LIMIT, UNAVAILABLE, TIMEOUT, INVALID_RESPONSE, REQUEST_FAILED
    }

    private final Reason reason;

    public BraveSearchException(Reason reason) {
        super(switch (reason) {
            case CONFIGURATION -> "Configure a chave de acesso da Brave Search.";
            case AUTHENTICATION -> "A Brave Search recusou a autenticação.";
            case RATE_LIMIT -> "O limite de uso da Brave Search foi atingido.";
            case UNAVAILABLE -> "A Brave Search está indisponível.";
            case TIMEOUT -> "A Brave Search excedeu o tempo de espera.";
            case INVALID_RESPONSE -> "A Brave Search retornou uma resposta inválida.";
            case REQUEST_FAILED -> "A Brave Search recusou a requisição.";
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
