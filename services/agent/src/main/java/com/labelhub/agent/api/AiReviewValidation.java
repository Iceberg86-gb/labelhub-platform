package com.labelhub.agent.api;

import java.util.List;

/** Response of POST /internal/ai-review/submissions/{id}/validate-review (ADR-019). */
public record AiReviewValidation(Boolean valid, String rulesVersion, List<ReviewValidationError> errors) {

    public boolean isValid() {
        return Boolean.TRUE.equals(valid);
    }

    public List<ReviewValidationError> errorsOrEmpty() {
        return errors == null ? List.of() : errors;
    }
}
