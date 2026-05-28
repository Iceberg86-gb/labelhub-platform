package com.labelhub.api.module.submission.validation;

import java.util.List;

public class AnswerValidationException extends RuntimeException {

    private final List<AnswerValidationError> errors;

    public AnswerValidationException(List<AnswerValidationError> errors) {
        super("Answer payload validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<AnswerValidationError> getErrors() {
        return errors;
    }
}
