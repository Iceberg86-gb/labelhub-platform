package com.labelhub.api.module.ai.exception;

public class AiReviewRuleNotFoundException extends RuntimeException {

    public AiReviewRuleNotFoundException(Long ruleId) {
        super("AI review rule not found: " + ruleId);
    }
}
