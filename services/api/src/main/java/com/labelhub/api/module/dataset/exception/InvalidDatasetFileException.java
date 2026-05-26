package com.labelhub.api.module.dataset.exception;

public class InvalidDatasetFileException extends RuntimeException {

    private final Integer lineNumber;

    public InvalidDatasetFileException(String message) {
        this(message, null);
    }

    public InvalidDatasetFileException(String message, Integer lineNumber) {
        super(message);
        this.lineNumber = lineNumber;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }
}
