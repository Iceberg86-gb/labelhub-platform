package com.labelhub.agent.api;

/** One problem reported by POST /internal/ai-review/submissions/{id}/validate-review (ADR-019). */
public record ReviewValidationError(String code, String path, String message) {
}
