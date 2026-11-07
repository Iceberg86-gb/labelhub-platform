package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.AiCallUsage;
import com.labelhub.api.generated.model.AiReviewPrecedent;
import com.labelhub.api.generated.model.InternalAiReviewPrecedents;
import com.labelhub.api.generated.model.DimensionScore;
import com.labelhub.api.generated.model.FieldFinding;
import com.labelhub.api.generated.model.InternalAiReviewContext;
import com.labelhub.api.generated.model.InternalAiReviewResult;
import com.labelhub.api.generated.model.InternalAiReviewResultRequest;
import com.labelhub.api.generated.model.InternalAiReviewValidation;
import com.labelhub.api.generated.model.InternalAiReviewValidationError;
import com.labelhub.api.generated.model.InternalAiReviewValidationRequest;
import com.labelhub.api.generated.web.InternalApi;
import com.labelhub.api.module.ai.precedent.Precedent;
import com.labelhub.api.module.ai.precedent.PrecedentService;
import com.labelhub.api.module.ai.service.DimensionScoreValue;
import com.labelhub.api.module.ai.service.InternalAiReviewResultCommand;
import com.labelhub.api.module.ai.service.AiReviewService;
import com.labelhub.api.module.ai.service.AiReviewOutputValidator;
import com.labelhub.api.module.ai.service.view.InternalAiReviewContextView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAiReviewController implements InternalApi {

    private final AiReviewService aiReviewService;
    private final AiReviewOutputValidator outputValidator;
    private final PrecedentService precedentService;

    public InternalAiReviewController(AiReviewService aiReviewService) {
        this(aiReviewService, new AiReviewOutputValidator(), null);
    }

    @Autowired
    public InternalAiReviewController(AiReviewService aiReviewService, AiReviewOutputValidator outputValidator,
                                      @Autowired(required = false) PrecedentService precedentService) {
        this.aiReviewService = aiReviewService;
        this.outputValidator = outputValidator;
        this.precedentService = precedentService;
    }

    /** ADR-021: read-only BM25 precedent search (also the backing of the H5 MCP search_precedents tool). */
    @Override
    public ResponseEntity<InternalAiReviewPrecedents> getAiReviewPrecedents(
        @PathVariable("submissionId") Long submissionId,
        Integer k
    ) {
        if (precedentService == null) {
            return ResponseEntity.notFound().build();
        }
        PrecedentService.Result result = precedentService.forSubmission(submissionId, k);
        InternalAiReviewPrecedents dto = new InternalAiReviewPrecedents();
        dto.setSubmissionId(submissionId);
        dto.setVersion(result.version());
        dto.setPrecedents(toPrecedentDtos(result.precedents()));
        dto.setPrecedentsHash(result.hash());
        return ResponseEntity.ok(dto);
    }

    static List<AiReviewPrecedent> toPrecedentDtos(List<Precedent> precedents) {
        return precedents.stream().map(p -> {
            AiReviewPrecedent dto = new AiReviewPrecedent();
            dto.setRank(p.rank());
            dto.setSubmissionId(p.submissionId());
            dto.setVerdict(AiReviewPrecedent.VerdictEnum.fromValue(p.verdict()));
            dto.setSource(AiReviewPrecedent.SourceEnum.fromValue(p.source()));
            dto.setSubmission(p.submission());
            return dto;
        }).toList();
    }

    static List<Precedent> fromPrecedentDtos(List<AiReviewPrecedent> dtos) {
        if (dtos == null) {
            return null;
        }
        return dtos.stream().map(d -> new Precedent(
            d.getRank() == null ? 0 : d.getRank(),
            d.getSubmissionId(),
            d.getVerdict() == null ? null : d.getVerdict().getValue(),
            d.getSource() == null ? null : d.getSource().getValue(),
            d.getSubmission()
        )).toList();
    }

    @Override
    public ResponseEntity<InternalAiReviewValidation> validateAiReviewOutput(
        @PathVariable("submissionId") Long submissionId,
        @Valid @RequestBody InternalAiReviewValidationRequest internalAiReviewValidationRequest
    ) {
        AiReviewOutputValidator.Context context = aiReviewService.getReviewValidationContext(submissionId);
        Object output = internalAiReviewValidationRequest.getOutput();
        List<AiReviewOutputValidator.Error> errors = outputValidator.validate(output, context);
        AiReviewOutputValidator.Routing routing = outputValidator.routing(output, context);
        InternalAiReviewValidation dto = new InternalAiReviewValidation();
        dto.setValid(errors.isEmpty());
        dto.setRoute(routing.route() == null ? null : InternalAiReviewValidation.RouteEnum.fromValue(routing.route()));
        dto.setFinalScore(routing.finalScore());
        dto.setRulesVersion(AiReviewOutputValidator.RULES_VERSION);
        dto.setErrors(errors.stream().map(error -> {
            InternalAiReviewValidationError item = new InternalAiReviewValidationError();
            item.setCode(InternalAiReviewValidationError.CodeEnum.fromValue(error.code()));
            item.setPath(error.path());
            item.setMessage(error.message());
            return item;
        }).toList());
        return ResponseEntity.ok(dto);
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
        dto.setPassThreshold(view.threshold());
        dto.setRejectThreshold(view.rejectFloor());
        dto.setScoringRuleVersion(view.scoringRuleVersion());
        dto.setBusinessPrompt(view.businessPrompt());
        dto.setRenderedPrompt(view.renderedPrompt());
        dto.setPrecedentVersion(view.precedentVersion());
        dto.setPrecedents(toPrecedentDtos(view.precedents() == null ? List.of() : view.precedents()));
        dto.setPrecedentsHash(view.precedentsHash());
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
            result.getResponsePayload() == null ? Map.of() : result.getResponsePayload(),
            fromPrecedentDtos(request.getPrecedents()),
            request.getPrecedentsHash()
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
