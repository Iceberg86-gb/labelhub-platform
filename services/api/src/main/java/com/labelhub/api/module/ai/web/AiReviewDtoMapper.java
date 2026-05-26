package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.AiCall;
import com.labelhub.api.generated.model.AiCallInField;
import com.labelhub.api.generated.model.AiCallStatus;
import com.labelhub.api.generated.model.AiCallUsage;
import com.labelhub.api.generated.model.AiReviewResult;
import com.labelhub.api.generated.model.FieldFinding;
import com.labelhub.api.generated.model.SubmissionAiProvenance;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.service.view.AiReviewResultView;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiReviewDtoMapper {

    public AiReviewResult toResult(AiReviewResultView view) {
        AiCallResult providerResult = view.providerResult();
        AiReviewResult dto = new AiReviewResult();
        dto.setAiCall(toAiCall(view.aiCall()));
        dto.setFieldFindings(providerResult.fieldFindings().stream().map(this::toFieldFinding).toList());
        dto.setOverallSuggestion(AiReviewResult.OverallSuggestionEnum.fromValue(providerResult.overallSuggestion()));
        dto.setConfidence(providerResult.confidence());
        dto.setSummary(providerResult.summary());
        dto.setIdempotencyHit(view.idempotencyHit());
        dto.setUsage(toUsage(view.aiCall()));
        return dto;
    }

    public SubmissionAiProvenance toProvenance(SubmissionAiProvenanceView view) {
        SubmissionAiProvenance dto = new SubmissionAiProvenance();
        dto.setSubmissionId(view.submissionId());
        dto.setAiCalls(view.aiCalls().stream().map(this::toAiCall).toList());
        dto.setFieldFindings(view.fieldRows().stream().map(this::toAiCallInField).toList());
        return dto;
    }

    public AiCall toAiCall(AiCallEntity entity) {
        AiCall dto = new AiCall();
        dto.setId(entity.getId());
        dto.setSubmissionId(entity.getSubmissionId());
        dto.setFieldPath(entity.getFieldPath());
        dto.setPurpose(entity.getPurpose());
        dto.setPromptVersion(entity.getPromptVersion());
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
