package com.eventledger.gateway.exception;

/**
 * Thrown when the Account Service cannot be reached or the circuit breaker is open.
 * Surfaced to clients as HTTP 503 via {@link GlobalExceptionHandler}.
 */
public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(String message) {
        super(message);
    }

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
