package com.cryptovault.exception;

/**
 * Thrown when login fails — for BOTH an unknown email and a wrong password. The message is
 * deliberately generic so the API never reveals whether an email is registered.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
