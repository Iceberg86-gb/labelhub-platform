package com.labelhub.agent.llm;

import com.labelhub.agent.api.ReviewValidationError;
import java.util.List;

/** A previous record_ai_review call (raw arguments as the model produced them) and why it failed validation. */
public record ReviewAttempt(String arguments, List<ReviewValidationError> errors) {
}
