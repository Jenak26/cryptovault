package com.cryptovault.exception;

/**
 * Thrown when a rate limit is exceeded.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
