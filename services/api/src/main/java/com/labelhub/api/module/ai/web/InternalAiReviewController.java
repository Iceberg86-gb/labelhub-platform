package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.AiCallUsage;
import com.labelhub.api.generated.model.DimensionScore;
import com.labelhub.api.generated.model.FieldFinding;
import com.labelhub.api.generated.model.InternalAiReviewContext;
import com.labelhub.api.generated.model.InternalAiReviewResult;
import com.labelhub.api.generated.model.InternalAiReviewResultRequest;
import com.labelhub.api.generated.web.InternalApi;
import com.labelhub.api.module.ai.service.DimensionScoreValue;
import com.labelhub.api.module.ai.service.InternalAiReviewResultCommand;
import com.labelhub.api.module.ai.service.AiReviewService;
import com.labelhub.api.module.ai.service.view.InternalAiReviewContextView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAiReviewController implements InternalApi {

    private final AiReviewService aiReviewService;

    public InternalAiReviewController(AiReviewService aiReviewService) {
        this.aiReviewService = aiReviewService;
    }

    @Override
    public ResponseEntity<InternalAiReviewContext> getAiReviewContext(@PathVariable("submissionId") Long submissionId) {
        return ResponseEntity.ok(toContext(aiReviewService.getInternalContext(submissionId)));
    }

    @Override
    public ResponseEntity<Void> reportAiReviewResult(
        @Valid @RequestBody InternalAiReviewResultRequest internalAiReviewResultRequest
    ) {
        aiReviewService.recordInternalResult(toCommand(internalAiReviewResultRequest));
        return ResponseEntity.noContent().build();
    }

    private InternalAiReviewContext toContext(InternalAiReviewContextView view) {
        InternalAiReviewContext dto = new InternalAiReviewContext();
        dto.setSubmissionId(view.submissionId());
        dto.setIdempotencyKey(view.idempotencyKey());
        dto.setPromptVersion(view.promptVersion());
        dto.setPromptVersionId(view.promptVersionId());
        dto.setAiReviewRuleId(view.aiReviewRuleId());
        dto.setProviderAdapterVersion(view.providerAdapterVersion());
        dto.setInput(view.input());
        dto.setInputHash(view.inputHash());
        dto.setDimensions(view.dimensions());
        dto.setThreshold(view.threshold());
        dto.setRejectFloor(view.rejectFloor());
        dto.setScoringRuleVersion(view.scoringRuleVersion());
        dto.setBusinessPrompt(view.businessPrompt());
        dto.setRenderedPrompt(view.renderedPrompt());
        return dto;
    }

    private InternalAiReviewResultCommand toCommand(InternalAiReviewResultRequest request) {
        InternalAiReviewResult result = request.getResult();
        return new InternalAiReviewResultCommand(
            request.getSubmissionId(),
            request.getIdempotencyKey(),
            result.getOverallSuggestion().getValue(),
            result.getFinalScore(),
            result.getDimensionScores() == null ? List.of() : result.getDimensionScores().stream()
                .map(this::toDimensionScoreValue)
                .toList(),
            result.getSummary(),
            result.getFieldFindings() == null ? List.of() : result.getFieldFindings().stream()
                .map(this::toProviderFieldFinding)
                .toList(),
            result.getRawResponse(),
            result.getTokenInput(),
            result.getTokenOutput(),
            toProviderUsage(result.getUsage()),
            result.getLatencyMs(),
            result.getModelProvider(),
            result.getModelName(),
            result.getResponsePayload() == null ? Map.of() : result.getResponsePayload()
        );
    }

    private DimensionScoreValue toDimensionScoreValue(DimensionScore dto) {
        return new DimensionScoreValue(dto.getDimension(), dto.getScore(), dto.getReason());
    }

    private com.labelhub.api.module.ai.provider.FieldFinding toProviderFieldFinding(FieldFinding dto) {
        return new com.labelhub.api.module.ai.provider.FieldFinding(
            dto.getFieldPath(),
            dto.getStableId(),
            dto.getLabel(),
            dto.getSeverity().getValue(),
            dto.getFinding(),
            dto.getConfidence()
        );
    }

    private com.labelhub.api.module.ai.provider.AiCallUsage toProviderUsage(AiCallUsage usage) {
        if (usage == null) {
            return null;
        }
        return new com.labelhub.api.module.ai.provider.AiCallUsage(
            usage.getPromptTokens(),
            usage.getCompletionTokens(),
            usage.getTotalTokens(),
            usage.getCacheHitTokens()
        );
    }
}
