package com.italo.ai.api;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("Credenciais inválidas, conta indisponível ou temporariamente bloqueada."); }
}
