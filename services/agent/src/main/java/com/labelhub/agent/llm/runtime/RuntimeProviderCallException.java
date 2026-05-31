package com.labelhub.agent.llm.runtime;

public class RuntimeProviderCallException extends RuntimeException {

    private final boolean retryable;
    private final String providerCode;
    private final Integer statusCode;

    public RuntimeProviderCallException(String message, boolean retryable, String providerCode, Integer statusCode) {
        super(message);
        this.retryable = retryable;
        this.providerCode = providerCode;
        this.statusCode = statusCode;
    }

    public RuntimeProviderCallException(
        String message,
        boolean retryable,
        String providerCode,
        Integer statusCode,
        Throwable cause
    ) {
        super(message, cause);
        this.retryable = retryable;
        this.providerCode = providerCode;
        this.statusCode = statusCode;
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
}
