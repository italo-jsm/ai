package com.italo.ai.user;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("Usuário não cadastrado.");
    }
}
