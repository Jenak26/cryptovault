package com.cryptovault.exception;

/**
 * Thrown when registering with an email that already exists. Unlike login, signup unavoidably
 * reveals that an email is taken, so this maps to a 409 with a clear message.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("Email already registered");
    }
}
