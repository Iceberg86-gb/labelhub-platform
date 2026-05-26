package com.labelhub.api.module.export.exception;

public class ExportFailureException extends RuntimeException {

    public ExportFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
