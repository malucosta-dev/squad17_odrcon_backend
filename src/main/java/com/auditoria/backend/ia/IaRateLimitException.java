package com.auditoria.backend.ia;

public class IaRateLimitException extends RuntimeException {

    private final long retryAfterSeconds;

    public IaRateLimitException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
