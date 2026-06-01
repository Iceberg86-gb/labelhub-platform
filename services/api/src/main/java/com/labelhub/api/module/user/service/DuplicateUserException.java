package com.labelhub.api.module.user.service;

public class DuplicateUserException extends RuntimeException {

    private final String field;
    private final String reason;

    public DuplicateUserException(String field, String reason) {
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
