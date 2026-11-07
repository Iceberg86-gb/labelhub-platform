package com.labelhub.agent.loop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelhub.agent.api.AiCallUsagePayload;
import com.labelhub.agent.api.AiReviewApiClient;
import com.labelhub.agent.api.AiReviewContext;
import com.labelhub.agent.api.AiReviewResultPayload;
import com.labelhub.agent.api.AiReviewValidation;
import com.labelhub.agent.api.DimensionScorePayload;
import com.labelhub.agent.api.FieldFindingPayload;
import com.labelhub.agent.api.ReviewValidationError;
import com.labelhub.agent.llm.AiReviewProvider;
import com.labelhub.agent.llm.ReviewAttempt;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** ADR-019: every branch of produce -> validate-review -> correct. */
class AiReviewAgentLoopTest {

    private static final ReviewValidationError CONFLICT =
        new ReviewValidationError("suggestion_score_conflict", "overallSuggestion", "reject contradicts score 0.3500");
    private static final ReviewValidationError UNKNOWN =
        new ReviewValidationError("unknown_field", "fieldFindings[0].stableId", "stableId answer-1 does not exist");

    private final AiReviewProvider provider = mock(AiReviewProvider.class);
    private final AiReviewApiClient api = mock(AiReviewApiClient.class);
    private final AiReviewContext context = context();

    @Test
    void valid_first_output_is_reported_as_is_with_one_step_of_provenance() {
        AiReviewResultPayload first = result("pass", "a1", 100, 20, 300);
        when(provider.review(context)).thenReturn(first);
        when(api.validateReview(300L, first.responsePayload())).thenReturn(valid());

        AiReviewResultPayload out = loop(3).run(context);

        assertThat(out.overallSuggestion()).isEqualTo("pass");
        assertThat(out.tokenInput()).isEqualTo(100);
        assertThat(loopSummary(out)).containsEntry("outcome", "valid").containsEntry("steps", 1).containsEntry("rulesVersion", "review-output-v1");
        verify(provider, never()).correct(any(), anyList());
    }

    @Test
    void correctable_error_is_corrected_with_feedback_and_tokens_latency_are_summed() {
        AiReviewResultPayload first = result("reject", "a1", 100, 20, 300);
        AiReviewResultPayload second = result("manual_review", "a2", 150, 25, 400);
        when(provider.review(context)).thenReturn(first);
        when(api.validateReview(300L, first.responsePayload())).thenReturn(invalid(UNKNOWN));
        when(provider.correct(eq(context), anyList())).thenReturn(second);
        when(api.validateReview(300L, second.responsePayload())).thenReturn(valid());

        AiReviewResultPayload out = loop(3).run(context);

        ArgumentCaptor<List<ReviewAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(provider).correct(eq(context), attempts.capture());
        assertThat(attempts.getValue()).containsExactly(new ReviewAttempt("a1", List.of(UNKNOWN)));
        assertThat(out.overallSuggestion()).isEqualTo("manual_review");
        assertThat(out.rawResponse()).isEqualTo("a2");
        assertThat(out.tokenInput()).isEqualTo(250);
        assertThat(out.tokenOutput()).isEqualTo(45);
        assertThat(out.latencyMs()).isEqualTo(700);
        assertThat(out.usage()).isEqualTo(new AiCallUsagePayload(250, 45, 295, null));
        assertThat(loopSummary(out)).containsEntry("outcome", "corrected").containsEntry("steps", 2);
        List<?> steps = (List<?>) out.responsePayload().get("agentSteps");
        assertThat(steps).hasSize(2);
        assertThat(asMap(steps.get(0))).containsEntry("valid", false)
            .containsEntry("errors", List.of(Map.of("code", "unknown_field", "path", "fieldFindings[0].stableId")));
        assertThat(asMap(steps.get(1))).containsEntry("valid", true).containsEntry("promptVersion", "h2-validate-loop-v2");
    }

    @Test
    void still_invalid_after_max_steps_downgrades_to_manual_review_and_drops_unknown_field_findings() {
        AiReviewResultPayload first = result("pass", "a1", 10, 1, 1);
        AiReviewResultPayload second = result("pass", "a2", 10, 1, 1);
        AiReviewResultPayload third = result("pass", "a3", 10, 1, 1);
        when(provider.review(context)).thenReturn(first);
        when(provider.correct(eq(context), anyList())).thenReturn(second, third);
        when(api.validateReview(eq(300L), any())).thenReturn(invalid(UNKNOWN, CONFLICT));

        AiReviewResultPayload out = loop(3).run(context);

        verify(provider, times(2)).correct(eq(context), anyList());
        verify(api, times(3)).validateReview(eq(300L), any());
        assertThat(out.overallSuggestion()).isEqualTo("manual_review");
        assertThat(out.fieldFindings()).extracting(FieldFindingPayload::stableId).containsExactly("reason");
        assertThat(out.responsePayload()).containsEntry("overallSuggestion", "manual_review").containsEntry("modelOverallSuggestion", "pass");
        assertThat((List<?>) out.responsePayload().get("fieldFindings")).hasSize(1);
        assertThat(loopSummary(out)).containsEntry("outcome", "downgraded").containsEntry("steps", 3).containsEntry("maxSteps", 3);
        assertThat(out.tokenInput()).isEqualTo(30);
    }

    @Test
    void the_correction_history_grows_by_one_attempt_per_failed_step() {
        AiReviewResultPayload first = result("pass", "a1", 1, 1, 1);
        AiReviewResultPayload second = result("pass", "a2", 1, 1, 1);
        AiReviewResultPayload third = result("pass", "a3", 1, 1, 1);
        when(provider.review(context)).thenReturn(first);
        when(provider.correct(eq(context), anyList())).thenReturn(second, third);
        when(api.validateReview(eq(300L), any())).thenReturn(invalid(UNKNOWN));

        loop(3).run(context);

        ArgumentCaptor<List<ReviewAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(provider, times(2)).correct(eq(context), attempts.capture());
        assertThat(attempts.getAllValues().get(0)).extracting(ReviewAttempt::arguments).containsExactly("a1");
        assertThat(attempts.getAllValues().get(1)).extracting(ReviewAttempt::arguments).containsExactly("a1", "a2");
    }

    @Test
    void provider_without_correction_support_is_downgraded_immediately() {
        AiReviewResultPayload first = result("reject", "a1", 1, 1, 1);
        when(provider.review(context)).thenReturn(first);
        when(provider.correct(eq(context), anyList())).thenThrow(new UnsupportedOperationException("no"));
        when(api.validateReview(eq(300L), any())).thenReturn(invalid(UNKNOWN));

        AiReviewResultPayload out = loop(3).run(context);

        assertThat(out.overallSuggestion()).isEqualTo("manual_review");
        assertThat(loopSummary(out)).containsEntry("outcome", "downgraded_no_correction_support").containsEntry("steps", 1);
    }

    @Test
    void judgement_inconsistency_is_flagged_and_never_sent_to_the_model() {
        AiReviewResultPayload first = result("reject", "a1", 1, 1, 1);
        when(provider.review(context)).thenReturn(first);
        when(api.validateReview(eq(300L), any())).thenReturn(invalid(CONFLICT));

        AiReviewResultPayload out = loop(3).run(context);

        verify(provider, never()).correct(any(), anyList());
        assertThat(out.overallSuggestion()).isEqualTo("manual_review");
        assertThat(out.fieldFindings()).hasSize(2);  // nothing to drop: no unknown_field
        assertThat(out.responsePayload()).containsEntry("modelOverallSuggestion", "reject");
        assertThat(loopSummary(out)).containsEntry("outcome", "flagged").containsEntry("steps", 1).containsEntry("version", "h2-validate-loop-v2");
    }

    @Test
    void only_correctable_errors_are_fed_back_and_a_remaining_conflict_is_flagged() {
        AiReviewResultPayload first = result("reject", "a1", 1, 1, 1);
        AiReviewResultPayload second = result("reject", "a2", 1, 1, 1);
        when(provider.review(context)).thenReturn(first);
        when(api.validateReview(300L, first.responsePayload())).thenReturn(invalid(UNKNOWN, CONFLICT));
        when(provider.correct(eq(context), anyList())).thenReturn(second);
        when(api.validateReview(300L, second.responsePayload())).thenReturn(invalid(CONFLICT));

        AiReviewResultPayload out = loop(3).run(context);

        ArgumentCaptor<List<ReviewAttempt>> attempts = ArgumentCaptor.forClass(List.class);
        verify(provider).correct(eq(context), attempts.capture());
        assertThat(attempts.getValue().get(0).errors()).containsExactly(UNKNOWN);
        assertThat(out.rawResponse()).isEqualTo("a2");
        assertThat(loopSummary(out)).containsEntry("outcome", "flagged").containsEntry("steps", 2);
    }

    @Test
    void a_correction_that_changes_the_routing_zone_is_discarded_and_the_first_answer_is_kept() {
        AiReviewResultPayload first = result("reject", "a1", 100, 10, 100);
        AiReviewResultPayload moved = result("reject", "a2", 150, 15, 200);
        when(provider.review(context)).thenReturn(first);
        when(api.validateReview(300L, first.responsePayload())).thenReturn(invalidIn("manual_review", UNKNOWN, CONFLICT));
        when(provider.correct(eq(context), anyList())).thenReturn(moved);
        when(api.validateReview(300L, moved.responsePayload())).thenReturn(validIn("reject"));

        AiReviewResultPayload out = loop(3).run(context);

        assertThat(out.rawResponse()).isEqualTo("a1");
        assertThat(out.overallSuggestion()).isEqualTo("manual_review");
        assertThat(out.fieldFindings()).extracting(FieldFindingPayload::stableId).containsExactly("reason");
        assertThat(out.tokenInput()).isEqualTo(250);  // the discarded call still cost tokens
        assertThat(loopSummary(out)).containsEntry("outcome", "judgement_changed").containsEntry("steps", 2);
    }

    @Test
    void a_failed_correction_turn_keeps_the_validated_first_answer() {
        AiReviewResultPayload first = result("pass", "a1", 1, 1, 1);
        when(provider.review(context)).thenReturn(first);
        when(api.validateReview(eq(300L), any())).thenReturn(invalid(UNKNOWN));
        when(provider.correct(eq(context), anyList())).thenThrow(new IllegalStateException("provider returned no tool call"));

        AiReviewResultPayload out = loop(3).run(context);

        assertThat(out.rawResponse()).isEqualTo("a1");
        assertThat(out.overallSuggestion()).isEqualTo("manual_review");
        assertThat(loopSummary(out)).containsEntry("outcome", "correction_failed");
    }

    @Test
    void validation_endpoint_failure_propagates_so_the_outbox_retries_instead_of_reporting_unvalidated() {
        AiReviewResultPayload first = result("pass", "a1", 1, 1, 1);
        when(provider.review(context)).thenReturn(first);
        when(api.validateReview(eq(300L), any())).thenThrow(new IllegalStateException("api down"));

        assertThatThrownBy(() -> loop(3).run(context)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabled_loop_is_exactly_the_h1_single_call() {
        AiReviewResultPayload first = result("reject", "a1", 1, 1, 1);
        when(provider.review(context)).thenReturn(first);

        assertThat(new AiReviewAgentLoop(provider, api, false, 3).run(context)).isSameAs(first);
        verify(api, never()).validateReview(any(), any());
    }

    @Test
    void max_steps_below_one_is_rejected() {
        assertThatThrownBy(() -> new AiReviewAgentLoop(provider, api, true, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private AiReviewAgentLoop loop(int maxSteps) {
        return new AiReviewAgentLoop(provider, api, true, maxSteps);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loopSummary(AiReviewResultPayload out) {
        return (Map<String, Object>) out.responsePayload().get("agentLoop");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static AiReviewValidation valid() {
        return validIn("manual_review");
    }

    private static AiReviewValidation validIn(String route) {
        return new AiReviewValidation(true, "review-output-v1", List.of(), route, new BigDecimal("0.35"));
    }

    private static AiReviewValidation invalid(ReviewValidationError... errors) {
        return invalidIn("manual_review", errors);
    }

    private static AiReviewValidation invalidIn(String route, ReviewValidationError... errors) {
        return new AiReviewValidation(false, "review-output-v1", List.of(errors), route, new BigDecimal("0.35"));
    }

    private static AiReviewResultPayload result(String suggestion, String raw, int in, int out, int latency) {
        List<FieldFindingPayload> findings = List.of(
            new FieldFindingPayload("answer", "answer-1", null, "info", "bad id", null),
            new FieldFindingPayload("reason", "reason", null, "info", "ok", null)
        );
        Map<String, Object> payload = Map.of(
            "overallSuggestion", suggestion,
            "raw", raw,
            "fieldFindings", List.of(Map.of("fieldPath", "answer", "stableId", "answer-1"), Map.of("fieldPath", "reason", "stableId", "reason"))
        );
        return new AiReviewResultPayload(suggestion, new BigDecimal("0.35"),
            List.of(new DimensionScorePayload("overall", new BigDecimal("0.35"), "r")), "s", findings, raw, in, out,
            new AiCallUsagePayload(in, out, in + out, null), latency, "openai-compatible", "m", payload);
    }

    private static AiReviewContext context() {
        return new AiReviewContext(300L, "key", "v1", 7L, null, "agent-default-v1", Map.of(), "hash", List.of("overall"),
            new BigDecimal("0.80"), new BigDecimal("0.20"), new BigDecimal("0.80"), new BigDecimal("0.20"),
            "equal-weight-three-zone-v2", "biz", "rendered", null, List.of(), null);
    }
}
