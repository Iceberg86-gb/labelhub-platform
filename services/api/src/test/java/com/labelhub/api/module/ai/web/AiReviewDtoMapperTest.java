package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.AiCallStatus;
import com.labelhub.api.generated.model.AiReviewResult;
import com.labelhub.api.generated.model.FieldFinding;
import com.labelhub.api.generated.model.SubmissionAiProvenance;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.service.view.AiReviewResultView;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewDtoMapperTest {

    private final AiReviewDtoMapper mapper = new AiReviewDtoMapper();

    @Test
    void toResult_maps_ai_call_and_field_findings_without_raw_payload_fields() {
        AiReviewResult result = mapper.toResult(new AiReviewResultView(
            aiCall(),
            providerResult(),
            List.of(fieldRow()),
            true
        ));

        assertThat(result.getIdempotencyHit()).isTrue();
        assertThat(result.getOverallSuggestion()).isEqualTo(AiReviewResult.OverallSuggestionEnum.LOOKS_GOOD);
        assertThat(result.getConfidence()).isEqualByComparingTo("0.90");
        assertThat(result.getAiCall().getProviderName()).isEqualTo("mock");
        assertThat(result.getAiCall().getPromptVersionId()).isEqualTo(700L);
        assertThat(result.getAiCall().getAiReviewRuleId()).isEqualTo(19L);
        assertThat(result.getAiCall().getProviderAdapterVersion()).isEqualTo("agent-default-v1");
        assertThat(result.getAiCall().getStatus()).isEqualTo(AiCallStatus.COMPLETED);
        assertThat(result.getAiCall().getCost()).isEqualByComparingTo("0.000100");
        assertThat(result.getAiCall().getCreatedAt()).isEqualTo(OffsetDateTime.of(2026, 5, 25, 12, 0, 0, 0, ZoneOffset.UTC));
        assertThat(result.getAiCall().getOutputHash()).hasSize(64);
        assertThat(result.getFieldFindings()).singleElement()
            .satisfies(finding -> {
                assertThat(finding.getFieldPath()).isEqualTo("field-title");
                assertThat(finding.getSeverity()).isEqualTo(FieldFinding.SeverityEnum.INFO);
                assertThat(finding.getConfidence()).isEqualByComparingTo("0.90");
            });
    }

    @Test
    void toProvenance_maps_ai_calls_and_field_rows() {
        SubmissionAiProvenance result = mapper.toProvenance(new SubmissionAiProvenanceView(
            300L,
            List.of(aiCall()),
            List.of(fieldRow())
        ));

        assertThat(result.getSubmissionId()).isEqualTo(300L);
        assertThat(result.getAiCalls()).singleElement()
            .satisfies(aiCall -> {
                assertThat(aiCall.getProviderName()).isEqualTo("mock");
                assertThat(aiCall.getPromptVersionId()).isEqualTo(700L);
                assertThat(aiCall.getAiReviewRuleId()).isEqualTo(19L);
                assertThat(aiCall.getProviderAdapterVersion()).isEqualTo("agent-default-v1");
                assertThat(aiCall.getOutputHash()).hasSize(64);
            });
        assertThat(result.getFieldFindings()).singleElement()
            .satisfies(row -> {
                assertThat(row.getFieldPath()).isEqualTo("field-title");
                assertThat(row.getAccepted()).isFalse();
            });
    }

    @Test
    void toAiCall_uses_adapter_placeholder_for_legacy_rows_without_backfilled_value() {
        AiCallEntity entity = aiCall();
        entity.setProviderAdapterVersion(null);

        assertThat(mapper.toAiCall(entity).getProviderAdapterVersion()).isEqualTo("agent-default-v1");
    }

    private AiCallResult providerResult() {
        com.labelhub.api.module.ai.provider.FieldFinding finding =
            new com.labelhub.api.module.ai.provider.FieldFinding(
                "field-title",
                "field-title",
                "标题",
                "info",
                "looks fine",
                new BigDecimal("0.90")
            );
        return new AiCallResult(
            Map.of(
                "overallSuggestion", "looks_good",
                "confidence", new BigDecimal("0.90"),
                "summary", "summary",
                "fieldFindings", List.of(Map.of(
                    "fieldPath", "field-title",
                    "stableId", "field-title",
                    "label", "标题",
                    "severity", "info",
                    "finding", "looks fine",
                    "confidence", new BigDecimal("0.90")
                ))
            ),
            "looks_good",
            new BigDecimal("0.90"),
            "summary",
            List.of(finding),
            10,
            20,
            new BigDecimal("0.000100"),
            100,
            null
        );
    }

    private AiCallEntity aiCall() {
        AiCallEntity entity = new AiCallEntity();
        entity.setId(900L);
        entity.setSubmissionId(300L);
        entity.setPurpose("submission_review");
        entity.setPromptVersion("promptVersion#1");
        entity.setPromptVersionId(700L);
        entity.setAiReviewRuleId(19L);
        entity.setProviderAdapterVersion("agent-default-v1");
        entity.setModelProvider("mock");
        entity.setModelName("mock-v1");
        entity.setInputHash("a".repeat(64));
        entity.setOutputHash("b".repeat(64));
        entity.setTokenInput(10);
        entity.setTokenOutput(20);
        entity.setCostDecimal(new BigDecimal("0.000100"));
        entity.setLatencyMs(100);
        entity.setStatus("completed");
        entity.setIdempotencyKey("submission:300:provider:mock:model:mock-v1:promptVersionId:700:adapter:agent-default-v1");
        entity.setCreatedAt(LocalDateTime.parse("2026-05-25T12:00:00"));
        entity.setCompletedAt(LocalDateTime.parse("2026-05-25T12:00:00"));
        entity.setRequestPayload(Map.of("secret", "not exposed"));
        entity.setResponsePayload(providerResult().output());
        return entity;
    }

    private AiCallInFieldEntity fieldRow() {
        AiCallInFieldEntity entity = new AiCallInFieldEntity();
        entity.setId(901L);
        entity.setSubmissionId(300L);
        entity.setFieldPath("field-title");
        entity.setAiCallId(900L);
        entity.setAccepted(false);
        entity.setUserModifiedAfter(false);
        entity.setOrdinal(1);
        entity.setCreatedAt(LocalDateTime.parse("2026-05-25T12:00:00"));
        return entity;
    }
}
