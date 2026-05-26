package com.labelhub.api.module.ai.exception;

public class AiProviderFailureException extends RuntimeException {

    public AiProviderFailureException(String message) {
        super(message);
    }

    public AiProviderFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
