package com.italo.ai.api;

import com.italo.ai.user.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail unauthorized(InvalidCredentialsException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, failure.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail notFound(UserNotFoundException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, failure.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException failure) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O nome de usuário ou o ID do Telegram já está cadastrado.");
    }
}
