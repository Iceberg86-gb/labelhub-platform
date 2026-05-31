package com.labelhub.api.module.ai.providerconfig;

public class InvalidLlmProviderConfigException extends RuntimeException {

    public InvalidLlmProviderConfigException(String message) {
        super(message);
    }
}
