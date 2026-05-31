package com.labelhub.api.module.ai.providerconfig;

public class LlmProviderConfigNotFoundException extends RuntimeException {

    public LlmProviderConfigNotFoundException(Long id) {
        super("LLM provider configuration not found: " + id);
    }
}
