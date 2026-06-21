package com.cryptovault.exception;

import io.jsonwebtoken.JwtException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into clean JSON responses with the right status codes.
 *
 * <p>Discipline: messages here never echo passwords, hashes, or tokens, and login failures stay
 * generic so the API does not reveal whether an email exists.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Unknown email or wrong password — generic, no existence leak. */
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return Map.of("error", ex.getMessage());
    }

    /** Duplicate email on register. */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleEmailExists(EmailAlreadyExistsException ex) {
        return Map.of("error", ex.getMessage());
    }

    /** Bean-validation failures on request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validation failed");
        body.put("fields", fields);
        return body;
    }

    /** Missing Authorization header on logout. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleMissingHeader(MissingRequestHeaderException ex) {
        return Map.of("error", "Missing or invalid token");
    }

    /** Malformed, tampered, or expired token on logout. */
    @ExceptionHandler(JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleJwt(JwtException ex) {
        return Map.of("error", "Missing or invalid token");
    }
}
