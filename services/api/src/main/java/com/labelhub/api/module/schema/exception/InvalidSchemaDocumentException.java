package com.labelhub.api.module.schema.exception;

public class InvalidSchemaDocumentException extends RuntimeException {
    private final String field;
    private final String reason;

    public InvalidSchemaDocumentException(String field, String reason) {
        super("Invalid schema field '" + field + "': " + reason);
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
