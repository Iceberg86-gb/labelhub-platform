package com.labelhub.agent.llm;

import com.labelhub.agent.api.ReviewValidationError;
import java.util.List;

/**
 * ADR-019 correction prompt. Must render byte-identical to packages/contracts/fixtures/review-loop-feedback.json
 * (and to evals/labelhub_evals/review_loop.py); ReviewFeedbackTest pins it.
 */
public final class ReviewFeedback {

    public static final String PROMPT_VERSION = "h2-validate-loop-v2";
    static final String HEADER = "Your record_ai_review call failed validation:";
    static final String FOOTER = "Call record_ai_review again with a complete, corrected result. Fix exactly the problems listed: "
        + "only reference fields that exist, and score every configured dimension exactly once. Keep your judgement of the "
        + "submission unchanged: the same overallSuggestion and the same scores.";

    private ReviewFeedback() {
    }

    public static String render(List<ReviewValidationError> errors) {
        StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (ReviewValidationError error : errors) {
            text.append("- [").append(error.code()).append("] ").append(error.path()).append(": ").append(error.message()).append('\n');
        }
        return text.append(FOOTER).toString();
    }
}
