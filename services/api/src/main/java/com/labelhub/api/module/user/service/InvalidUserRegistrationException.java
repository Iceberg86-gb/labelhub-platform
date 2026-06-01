package com.labelhub.api.module.user.service;

public class InvalidUserRegistrationException extends RuntimeException {

    private final String field;
    private final String reason;

    public InvalidUserRegistrationException(String field, String reason) {
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
