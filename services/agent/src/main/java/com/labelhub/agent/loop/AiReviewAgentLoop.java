package com.labelhub.agent.loop;

import com.labelhub.agent.api.AiCallUsagePayload;
import com.labelhub.agent.api.AiReviewApiClient;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.api.AiReviewValidation;
import com.labelhub.agent.api.FieldFindingPayload;
import com.labelhub.agent.api.ReviewValidationError;
import com.labelhub.agent.llm.AiReviewProvider;
import com.labelhub.agent.llm.ReviewAttempt;
import com.labelhub.agent.llm.ReviewFeedback;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ADR-019 (ADR-017 H2): produce -> validate-review -> correct, at most {@code maxSteps} model calls.
 *
 * <ul>
 *   <li>Valid at any step: the result is reported as produced.</li>
 *   <li>Still invalid after {@code maxSteps}, or the provider cannot hold a correction turn: deterministic
 *       downgrade (overallSuggestion = manual_review, findings on unknown fields dropped).</li>
 *   <li>validate-review itself failing propagates, so the outbox retries; nothing is reported unvalidated.</li>
 * </ul>
 * Still one ai_calls row per review: tokens and latency are summed over steps, and every step's errors are kept
 * in responsePayload.agentSteps as provenance.
 */
@Component
public class AiReviewAgentLoop {

    public static final String LOOP_VERSION = "h2-validate-loop-v1";
    private static final Pattern FINDING_INDEX = Pattern.compile("^fieldFindings\\[(\\d+)]");

    private final AiReviewProvider provider;
    private final AiReviewApiClient apiClient;
    private final boolean enabled;
    private final int maxSteps;

    public AiReviewAgentLoop(
        AiReviewProvider provider,
        AiReviewApiClient apiClient,
        @Value("${labelhub.agent.review-loop.enabled:true}") boolean enabled,
        @Value("${labelhub.agent.review-loop.max-steps:3}") int maxSteps
    ) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("labelhub.agent.review-loop.max-steps must be >= 1");
        }
        this.provider = provider;
        this.apiClient = apiClient;
        this.enabled = enabled;
        this.maxSteps = maxSteps;
    }

    public AiReviewResultPayload run(AiReviewContext context) {
        AiReviewResultPayload current = provider.review(context);
        if (!enabled) {
            return current;
        }
        List<AiReviewResultPayload> produced = new ArrayList<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        List<ReviewAttempt> attempts = new ArrayList<>();
        String rulesVersion = null;
        for (int step = 1; ; step++) {
            produced.add(current);
            AiReviewValidation validation = apiClient.validateReview(context.submissionId(), current.responsePayload());
            if (validation == null) {
                throw new IllegalStateException("validate-review returned no body");
            }
            rulesVersion = validation.rulesVersion();
            List<ReviewValidationError> errors = validation.errorsOrEmpty();
            steps.add(step(step, errors));
            if (validation.isValid()) {
                return finish(current, produced, steps, step == 1 ? "valid" : "corrected", rulesVersion);
            }
            if (step >= maxSteps) {
                return downgrade(current, errors, produced, steps, "downgraded", rulesVersion);
            }
            attempts.add(new ReviewAttempt(current.rawResponse(), errors));
            try {
                current = provider.correct(context, List.copyOf(attempts));
            } catch (UnsupportedOperationException unsupported) {
                return downgrade(current, errors, produced, steps, "downgraded_no_correction_support", rulesVersion);
            }
        }
    }

    private static Map<String, Object> step(int step, List<ReviewValidationError> errors) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("step", step);
        row.put("promptVersion", step == 1 ? "initial" : ReviewFeedback.PROMPT_VERSION);
        row.put("valid", errors.isEmpty());
        row.put("errors", errors.stream().map(e -> Map.of("code", e.code(), "path", e.path())).toList());
        return row;
    }

    private AiReviewResultPayload finish(
        AiReviewResultPayload result,
        List<AiReviewResultPayload> produced,
        List<Map<String, Object>> steps,
        String outcome,
        String rulesVersion
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(result.responsePayload() == null ? Map.of() : result.responsePayload());
        payload.put("agentLoop", loopSummary(outcome, steps.size(), rulesVersion));
        payload.put("agentSteps", steps);
        return withTotals(result, result.overallSuggestion(), result.fieldFindings(), payload, produced);
    }

    private AiReviewResultPayload downgrade(
        AiReviewResultPayload result,
        List<ReviewValidationError> errors,
        List<AiReviewResultPayload> produced,
        List<Map<String, Object>> steps,
        String outcome,
        String rulesVersion
    ) {
        Set<Integer> unknownFindings = new HashSet<>();
        for (ReviewValidationError error : errors) {
            if ("unknown_field".equals(error.code())) {
                Matcher matcher = FINDING_INDEX.matcher(error.path());
                if (matcher.find()) {
                    unknownFindings.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        List<FieldFindingPayload> kept = new ArrayList<>();
        List<FieldFindingPayload> findings = result.fieldFindings() == null ? List.of() : result.fieldFindings();
        for (int i = 0; i < findings.size(); i++) {
            if (!unknownFindings.contains(i)) {
                kept.add(findings.get(i));
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>(result.responsePayload() == null ? Map.of() : result.responsePayload());
        payload.put("modelOverallSuggestion", result.overallSuggestion());
        payload.put("overallSuggestion", "manual_review");
        if (payload.get("fieldFindings") instanceof List<?> rawFindings) {
            List<Object> keptRaw = new ArrayList<>();
            for (int i = 0; i < rawFindings.size(); i++) {
                if (!unknownFindings.contains(i)) {
                    keptRaw.add(rawFindings.get(i));
                }
            }
            payload.put("fieldFindings", keptRaw);
        }
        payload.put("agentLoop", loopSummary(outcome, steps.size(), rulesVersion));
        payload.put("agentSteps", steps);
        return withTotals(result, "manual_review", kept, payload, produced);
    }

    private Map<String, Object> loopSummary(String outcome, int steps, String rulesVersion) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("version", LOOP_VERSION);
        summary.put("outcome", outcome);
        summary.put("steps", steps);
        summary.put("maxSteps", maxSteps);
        summary.put("rulesVersion", rulesVersion);
        return summary;
    }

    private static AiReviewResultPayload withTotals(
        AiReviewResultPayload last,
        String overallSuggestion,
        List<FieldFindingPayload> findings,
        Map<String, Object> payload,
        List<AiReviewResultPayload> produced
    ) {
        return new AiReviewResultPayload(
            overallSuggestion,
            last.finalScore(),
            last.dimensionScores(),
            last.summary(),
            findings,
            last.rawResponse(),
            sum(produced.stream().map(AiReviewResultPayload::tokenInput).toList()),
            sum(produced.stream().map(AiReviewResultPayload::tokenOutput).toList()),
            usage(produced),
            sum(produced.stream().map(AiReviewResultPayload::latencyMs).toList()),
            last.modelProvider(),
            last.modelName(),
            payload
        );
    }

    /** Null when no step reported the metric; otherwise the sum of the steps that did. */
    private static Integer sum(List<Integer> values) {
        Integer total = null;
        for (Integer value : values) {
            if (value != null) {
                total = (total == null ? 0 : total) + value;
            }
        }
        return total;
    }

    private static AiCallUsagePayload usage(List<AiReviewResultPayload> produced) {
        List<AiCallUsagePayload> usages = produced.stream().map(AiReviewResultPayload::usage).filter(u -> u != null).toList();
        if (usages.isEmpty()) {
            return null;
        }
        return new AiCallUsagePayload(
            sum(usages.stream().map(AiCallUsagePayload::promptTokens).toList()),
            sum(usages.stream().map(AiCallUsagePayload::completionTokens).toList()),
            sum(usages.stream().map(AiCallUsagePayload::totalTokens).toList()),
            sum(usages.stream().map(AiCallUsagePayload::cacheHitTokens).toList())
        );
    }
}
