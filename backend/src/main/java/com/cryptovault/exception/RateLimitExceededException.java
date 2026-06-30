package com.cryptovault.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Too many login attempts. Please try again later.");
    }
}
