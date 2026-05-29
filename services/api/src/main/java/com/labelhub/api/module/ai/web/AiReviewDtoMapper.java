package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.AiCall;
import com.labelhub.api.generated.model.AiCallInField;
import com.labelhub.api.generated.model.AiCallStatus;
import com.labelhub.api.generated.model.AiCallUsage;
import com.labelhub.api.generated.model.AiReviewResult;
import com.labelhub.api.generated.model.DimensionScore;
import com.labelhub.api.generated.model.FieldFinding;
import com.labelhub.api.generated.model.SubmissionAiProvenance;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.service.view.AiReviewResultView;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiReviewDtoMapper {
    private static final String DEFAULT_PROVIDER_ADAPTER_VERSION = "agent-default-v1";

    public AiReviewResult toResult(AiReviewResultView view) {
        AiCallResult providerResult = view.providerResult();
        AiReviewResult dto = new AiReviewResult();
        dto.setAiCall(toAiCall(view.aiCall()));
        dto.setFieldFindings(providerResult.fieldFindings().stream().map(this::toFieldFinding).toList());
        dto.setOverallSuggestion(AiReviewResult.OverallSuggestionEnum.fromValue(providerResult.overallSuggestion()));
        dto.setConfidence(providerResult.confidence());
        dto.setSummary(providerResult.summary());
        dto.setDimensionScores(toDimensionScores(providerResult.output().get("dimensionScores")));
        dto.setIdempotencyHit(view.idempotencyHit());
        dto.setUsage(toUsage(view.aiCall()));
        return dto;
    }

    public SubmissionAiProvenance toProvenance(SubmissionAiProvenanceView view) {
        SubmissionAiProvenance dto = new SubmissionAiProvenance();
        dto.setSubmissionId(view.submissionId());
        dto.setAiCalls(view.aiCalls().stream().map(call -> toAiCall(call, view.exposeRawPrompt())).toList());
        dto.setFieldFindings(view.fieldRows().stream().map(this::toAiCallInField).toList());
        return dto;
    }

    public AiCall toAiCall(AiCallEntity entity) {
        return toAiCall(entity, false);
    }

    public AiCall toAiCall(AiCallEntity entity, boolean includeRawPrompt) {
        AiCall dto = new AiCall();
        dto.setId(entity.getId());
        dto.setSubmissionId(entity.getSubmissionId());
        dto.setFieldPath(entity.getFieldPath());
        dto.setPurpose(entity.getPurpose());
        dto.setPromptVersion(entity.getPromptVersion());
        dto.setPromptVersionId(entity.getPromptVersionId());
        dto.setAiReviewRuleId(entity.getAiReviewRuleId());
        dto.setProviderAdapterVersion(providerAdapterVersion(entity));
        dto.setProviderName(entity.getModelProvider());
        dto.setModelName(entity.getModelName());
        dto.setInputHash(entity.getInputHash());
        dto.setOutputHash(entity.getOutputHash());
        dto.setTokenInput(entity.getTokenInput());
        dto.setTokenOutput(entity.getTokenOutput());
        dto.setCost(entity.getCostDecimal());
        dto.setLatencyMs(entity.getLatencyMs());
        dto.setStatus(AiCallStatus.fromValue(entity.getStatus()));
        dto.setIdempotencyKey(entity.getIdempotencyKey());
        dto.setCreatedAt(offset(entity.getCreatedAt()));
        dto.setCompletedAt(offset(entity.getCompletedAt()));
        if (includeRawPrompt) {
            dto.setBusinessPrompt(stringValue(entity.getRequestPayload(), "businessPrompt"));
            dto.setRenderedPrompt(stringValue(entity.getRequestPayload(), "renderedPrompt"));
            dto.setRequestPayload(entity.getRequestPayload());
            dto.setResponsePayload(entity.getResponsePayload());
        }
        return dto;
    }

    private AiCallUsage toUsage(AiCallEntity entity) {
        if (entity.getPromptTokens() == null
            && entity.getCompletionTokens() == null
            && entity.getTotalTokens() == null
            && entity.getCacheHitTokens() == null) {
            return null;
        }
        AiCallUsage usage = new AiCallUsage();
        usage.setPromptTokens(entity.getPromptTokens());
        usage.setCompletionTokens(entity.getCompletionTokens());
        usage.setTotalTokens(entity.getTotalTokens());
        usage.setCacheHitTokens(entity.getCacheHitTokens());
        return usage;
    }

    private String providerAdapterVersion(AiCallEntity entity) {
        return entity.getProviderAdapterVersion() == null
            ? DEFAULT_PROVIDER_ADAPTER_VERSION
            : entity.getProviderAdapterVersion();
    }

    private List<DimensionScore> toDimensionScores(Object value) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        List<DimensionScore> scores = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            DimensionScore dto = new DimensionScore();
            Object dimension = map.get("dimension");
            if (dimension != null) {
                dto.setDimension(String.valueOf(dimension));
            }
            Object score = map.get("score");
            if (score != null) {
                dto.setScore(decimalValue(score));
            }
            Object reason = map.get("reason");
            if (reason != null) {
                dto.setReason(String.valueOf(reason));
            }
            scores.add(dto);
        }
        return scores;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public AiCallInField toAiCallInField(AiCallInFieldEntity entity) {
        AiCallInField dto = new AiCallInField();
        dto.setId(entity.getId());
        dto.setSubmissionId(entity.getSubmissionId());
        dto.setFieldPath(entity.getFieldPath());
        dto.setAiCallId(entity.getAiCallId());
        dto.setAccepted(entity.getAccepted());
        dto.setUserModifiedAfter(entity.getUserModifiedAfter());
        dto.setOrdinal(entity.getOrdinal());
        dto.setCreatedAt(offset(entity.getCreatedAt()));
        return dto;
    }

    public FieldFinding toFieldFinding(com.labelhub.api.module.ai.provider.FieldFinding finding) {
        FieldFinding dto = new FieldFinding();
        dto.setFieldPath(finding.fieldPath());
        dto.setStableId(finding.stableId());
        dto.setLabel(finding.label());
        dto.setSeverity(FieldFinding.SeverityEnum.fromValue(finding.severity()));
        dto.setFinding(finding.finding());
        dto.setConfidence(finding.confidence());
        return dto;
    }

    private java.time.OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
