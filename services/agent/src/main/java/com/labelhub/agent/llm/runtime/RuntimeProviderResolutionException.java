package com.labelhub.agent.llm.runtime;

public class RuntimeProviderResolutionException extends RuntimeException {

    private final String providerCode;

    public RuntimeProviderResolutionException(String message, String providerCode) {
        super(message);
        this.providerCode = providerCode;
    }

    public RuntimeProviderResolutionException(String message, String providerCode, Throwable cause) {
        super(message, cause);
        this.providerCode = providerCode;
    }

    public String getProviderCode() {
        return providerCode;
    }
}
