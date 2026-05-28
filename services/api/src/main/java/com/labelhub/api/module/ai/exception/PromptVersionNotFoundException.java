package com.labelhub.api.module.ai.exception;

public class PromptVersionNotFoundException extends RuntimeException {

    public PromptVersionNotFoundException(Long promptVersionId) {
        super("Prompt version not found: " + promptVersionId);
    }
}
