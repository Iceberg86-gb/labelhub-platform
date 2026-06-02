package com.labelhub.agent.llm.runtime;

public class RuntimeProviderCallException extends RuntimeException {

    private final boolean retryable;
    private final String providerCode;
    private final Integer statusCode;
    private final String providerBodySummary;

    public RuntimeProviderCallException(String message, boolean retryable, String providerCode, Integer statusCode) {
        this(message, retryable, providerCode, statusCode, null, null);
    }

    public RuntimeProviderCallException(
        String message,
        boolean retryable,
        String providerCode,
        Integer statusCode,
        Throwable cause
    ) {
        this(message, retryable, providerCode, statusCode, cause, null);
    }

    public RuntimeProviderCallException(
        String message,
        boolean retryable,
        String providerCode,
        Integer statusCode,
        Throwable cause,
        String providerBodySummary
    ) {
        super(message, cause);
        this.retryable = retryable;
        this.providerCode = providerCode;
        this.statusCode = statusCode;
        this.providerBodySummary = providerBodySummary;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getProviderBodySummary() {
        return providerBodySummary;
    }
}
