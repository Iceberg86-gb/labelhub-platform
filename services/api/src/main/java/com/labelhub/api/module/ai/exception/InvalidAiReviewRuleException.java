package com.labelhub.api.module.ai.exception;

public class InvalidAiReviewRuleException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidAiReviewRuleException(String field, String reason) {
        super(reason);
        this.field = field;
        this.reason = reason;
    }

    public String getField() {
        return field;
    }

    public String getReason() {
        return reason;
    }
}
