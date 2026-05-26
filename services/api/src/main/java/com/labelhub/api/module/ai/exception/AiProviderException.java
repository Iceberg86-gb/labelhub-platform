package com.labelhub.api.module.ai.exception;

public class AiProviderException extends RuntimeException {

    private final boolean retryable;
    private final String providerCode;
    private final Integer statusCode;

    public AiProviderException(String message, boolean retryable, String providerCode, Integer statusCode) {
        super(message);
        this.retryable = retryable;
        this.providerCode = providerCode;
        this.statusCode = statusCode;
    }

    public AiProviderException(String message, Throwable cause, boolean retryable, String providerCode, Integer statusCode) {
        super(message, cause);
        this.retryable = retryable;
        this.providerCode = providerCode;
        this.statusCode = statusCode;
    }

    public boolean isRetryable() { return retryable; }
    public String getProviderCode() { return providerCode; }
    public Integer getStatusCode() { return statusCode; }
}
