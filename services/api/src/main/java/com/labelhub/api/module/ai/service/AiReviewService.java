package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.entity.AiCallStatusCodes;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.exception.AiInputHashMismatchException;
import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.exception.AiProviderFailureException;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.exception.PromptVersionNotFoundException;
import com.labelhub.api.module.ai.mapper.AiCallInFieldMapper;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.ai.observability.AiIdempotencyMetrics;
import com.labelhub.api.module.ai.provider.AiCallRequest;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.AiProvider;
import com.labelhub.api.module.ai.provider.FieldFinding;
import com.labelhub.api.module.ai.provider.PromptTemplate;
import com.labelhub.api.module.ai.provider.ProviderInvocationResult;
import com.labelhub.api.module.ai.service.view.AiReviewResultView;
import com.labelhub.api.module.ai.service.view.InternalAiReviewContextView;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.quality.service.LedgerService;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.schema.runtime.SchemaRuntimeAdapter;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReviewService {

    static final String PROVIDER_ADAPTER_VERSION = "agent-default-v1";
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 160;

    private final SubmissionMapper submissionMapper;
    private final SchemaVersionMapper schemaVersionMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final TaskMapper taskMapper;
    private final AiCallMapper aiCallMapper;
    private final AiReviewRuleMapper aiReviewRuleMapper;
    private final AiCallInFieldMapper aiCallInFieldMapper;
    private final LedgerService ledgerService;
    private final PromptVersionService promptVersionService;
    private final Canonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AiProvider aiProvider;
    private final AiCallCostCalculator costCalculator;
    private final AiIdempotencyMetrics metrics;
    private final AiRetryPolicy retryPolicy;
    private final FailedAiCallRecorder failedCallRecorder;
    private final AuditLogService auditLogService;
    private final AiReviewScoringPolicy scoringPolicy;
    private final SchemaRuntimeAdapter schemaRuntimeAdapter;

    @Autowired
    public AiReviewService(
        SubmissionMapper submissionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DatasetItemMapper datasetItemMapper,
        TaskMapper taskMapper,
        AiCallMapper aiCallMapper,
        AiReviewRuleMapper aiReviewRuleMapper,
        AiCallInFieldMapper aiCallInFieldMapper,
        LedgerService ledgerService,
        PromptVersionService promptVersionService,
        Canonicalizer canonicalizer,
        ObjectMapper objectMapper,
        Clock clock,
        AiProvider aiProvider,
        AiCallCostCalculator costCalculator,
        AiIdempotencyMetrics metrics,
        AiRetryPolicy retryPolicy,
        FailedAiCallRecorder failedCallRecorder,
        AuditLogService auditLogService,
        AiReviewScoringPolicy scoringPolicy,
        SchemaRuntimeAdapter schemaRuntimeAdapter
    ) {
        this.submissionMapper = submissionMapper;
        this.schemaVersionMapper = schemaVersionMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.taskMapper = taskMapper;
        this.aiCallMapper = aiCallMapper;
        this.aiReviewRuleMapper = aiReviewRuleMapper;
        this.aiCallInFieldMapper = aiCallInFieldMapper;
        this.ledgerService = ledgerService;
        this.promptVersionService = promptVersionService;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.aiProvider = aiProvider;
        this.costCalculator = costCalculator;
        this.metrics = metrics;
        this.retryPolicy = retryPolicy;
        this.failedCallRecorder = failedCallRecorder;
        this.auditLogService = auditLogService;
        this.scoringPolicy = scoringPolicy;
        this.schemaRuntimeAdapter = schemaRuntimeAdapter;
    }

    public AiReviewService(
        SubmissionMapper submissionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DatasetItemMapper datasetItemMapper,
        TaskMapper taskMapper,
        AiCallMapper aiCallMapper,
        AiReviewRuleMapper aiReviewRuleMapper,
        AiCallInFieldMapper aiCallInFieldMapper,
        LedgerService ledgerService,
        PromptVersionService promptVersionService,
        Canonicalizer canonicalizer,
        ObjectMapper objectMapper,
        Clock clock,
        AiProvider aiProvider,
        AiCallCostCalculator costCalculator,
        AiIdempotencyMetrics metrics,
        AiRetryPolicy retryPolicy,
        FailedAiCallRecorder failedCallRecorder,
        AuditLogService auditLogService
    ) {
        this(submissionMapper, schemaVersionMapper, datasetItemMapper, taskMapper, aiCallMapper, aiReviewRuleMapper,
            aiCallInFieldMapper, ledgerService, promptVersionService, canonicalizer, objectMapper, clock, aiProvider,
            costCalculator, metrics, retryPolicy, failedCallRecorder, auditLogService,
            new AiReviewScoringPolicy(new AiReviewScoringProperties()), new SchemaRuntimeAdapter(objectMapper));
    }

    public AiReviewService(
        SubmissionMapper submissionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DatasetItemMapper datasetItemMapper,
        TaskMapper taskMapper,
        AiCallMapper aiCallMapper,
        AiReviewRuleMapper aiReviewRuleMapper,
        AiCallInFieldMapper aiCallInFieldMapper,
        LedgerService ledgerService,
        PromptVersionService promptVersionService,
        Canonicalizer canonicalizer,
        ObjectMapper objectMapper,
        Clock clock,
        AiProvider aiProvider,
        AiCallCostCalculator costCalculator,
        AiIdempotencyMetrics metrics,
        AiRetryPolicy retryPolicy,
        FailedAiCallRecorder failedCallRecorder
    ) {
        this(submissionMapper, schemaVersionMapper, datasetItemMapper, taskMapper, aiCallMapper, aiReviewRuleMapper,
            aiCallInFieldMapper, ledgerService, promptVersionService, canonicalizer, objectMapper, clock, aiProvider,
            costCalculator, metrics, retryPolicy, failedCallRecorder, AuditLogService.noop(),
            new AiReviewScoringPolicy(new AiReviewScoringProperties()), new SchemaRuntimeAdapter(objectMapper));
    }

    @Transactional
    public AiReviewResultView review(Long submissionId, Long ownerId, Long promptVersionId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new SubmissionNotFoundException(submissionId);
        }

        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(submission.getSchemaVersionId());
        DatasetItemEntity datasetItem = datasetItemMapper.selectById(submission.getDatasetItemId());
        AiReviewRuleEntity activeRule = activeReviewRule(task);
        Long aiReviewRuleId = activeRule == null ? null : activeRule.getId();
        Long effectivePromptVersionId = activeRule == null ? promptVersionId : activeRule.getCurrentPromptVersionId();
        PromptVersionEntity promptVersion = promptVersionService.findById(effectivePromptVersionId);
        if (promptVersion == null) {
            throw new PromptVersionNotFoundException(effectivePromptVersionId);
        }
        String promptVersionLabel = promptVersionLabel(promptVersion);
        String businessPrompt = promptVersion.getContent();
        String providerAdapterVersion = PROVIDER_ADAPTER_VERSION;
        Map<String, Object> input = buildInput(submission, schemaVersion, datasetItem, task);
        String inputHash = hash(input);
        String idempotencyKey = aiReviewIdempotencyKey(submissionId, promptVersion.getId(), aiReviewRuleId);

        AiCallEntity existing = aiCallMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (Objects.equals(existing.getInputHash(), inputHash)) {
                existing.setOutputHash(hash(existing.getResponsePayload()));
                List<AiCallInFieldEntity> rows =
                    aiCallInFieldMapper.selectBySubmissionAndAiCall(submissionId, existing.getId());
                metrics.recordHit(aiProvider.providerName());
                return new AiReviewResultView(existing, reconstructResult(existing), rows, true);
            }
            metrics.recordMismatch(aiProvider.providerName());
            throw new AiInputHashMismatchException(submissionId, idempotencyKey);
        }

        metrics.recordMiss(aiProvider.providerName());
        ProviderInvocationResult invocation;
        try {
            invocation = invokeProvider(
                submissionId,
                promptVersionLabel,
                businessPrompt,
                promptVersion.getId(),
                aiReviewRuleId,
                providerAdapterVersion,
                input,
                inputHash,
                idempotencyKey
            );
        } catch (AiProviderFailureException exception) {
            auditLogService.recordRequiresNew(
                AuditEventBuilder.forAction(AuditActions.AI_REVIEW_FAILED)
                    .actorSystem()
                    .resource("submission", submissionId)
                    .payload("submissionId", submissionId)
                    .payload("taskId", submission.getTaskId())
                    .payload("promptVersion", promptVersionLabel)
                    .payload("promptVersionId", promptVersion.getId())
                    .payload("aiReviewRuleId", aiReviewRuleId)
                    .payload("providerAdapterVersion", providerAdapterVersion)
                    .payload("provider", aiProvider.providerName())
                    .payload("model", aiProvider.modelName())
                    .payload("inputHash", inputHash)
                    .payload("idempotencyKey", idempotencyKey)
                    .payload("error", exception.getMessage())
                    .payload("providerError", providerError(exception))
            );
            throw exception;
        }
        AiCallResult result = normalizedResult(invocation.result(), activeRule);
        AiCallUsage usage = invocation.usage();
        Map<String, Object> responsePayload = persistedJsonShape(result.output());
        String outputHash = hash(responsePayload);
        Map<String, Object> requestPayload =
            aiRequestPayload(businessPrompt, PromptTemplate.build(businessPrompt, input, objectMapper), input, result.output());
        LocalDateTime now = LocalDateTime.now(clock);
        AiCallEntity aiCall = new AiCallEntity();
        aiCall.setSubmissionId(submissionId);
        aiCall.setPurpose("submission_review");
        aiCall.setPromptVersion(promptVersionLabel);
        aiCall.setPromptVersionId(promptVersion.getId());
        aiCall.setAiReviewRuleId(aiReviewRuleId);
        aiCall.setProviderAdapterVersion(providerAdapterVersion);
        aiCall.setModelProvider(aiProvider.providerName());
        aiCall.setModelName(aiProvider.modelName());
        aiCall.setInputHash(inputHash);
        aiCall.setRequestPayload(requestPayload);
        aiCall.setResponsePayload(responsePayload);
        aiCall.setScores(Map.of(
            "finalScore", result.confidence(),
            "dimensionScores", result.output().getOrDefault("dimensionScores", List.of())
        ));
        aiCall.setVerdict(result.overallSuggestion());
        aiCall.setTokenInput(result.tokenInput());
        aiCall.setTokenOutput(result.tokenOutput());
        aiCall.setCostDecimal(costCalculator.computeCost(aiProvider.modelName(), usage));
        aiCall.setPromptTokens(usage == null ? null : usage.promptTokens());
        aiCall.setCompletionTokens(usage == null ? null : usage.completionTokens());
        aiCall.setTotalTokens(usage == null ? null : usage.totalTokens());
        aiCall.setCacheHitTokens(usage == null ? null : usage.cacheHitTokens());
        aiCall.setLatencyMs(Math.toIntExact(result.latencyMs()));
        aiCall.setStatus(AiCallStatusCodes.COMPLETED);
        aiCall.setIdempotencyKey(idempotencyKey);
        aiCall.setCreatedAt(now);
        aiCall.setCompletedAt(now);
        requireOneRow(aiCallMapper.insert(aiCall), "insert ai_call");

        List<FieldFinding> fieldFindings = result.fieldFindings() == null ? List.of() : result.fieldFindings();
        List<AiCallInFieldEntity> rows = new ArrayList<>();
        for (FieldFinding finding : fieldFindings) {
            AiCallInFieldEntity row = fieldRow(submissionId, aiCall.getId(), finding.fieldPath(), now);
            requireOneRow(aiCallInFieldMapper.insert(row), "insert ai_call_in_field");
            rows.add(row);
        }
        if (!fieldFindings.isEmpty()) {
            List<QualityLedgerEntryEntity> ledgerEntries =
                ledgerService.appendAiFieldFindings(submissionId, submission.getTaskId(), aiCall.getId(), fieldFindings);
            auditLogService.record(
                AuditEventBuilder.forAction(AuditActions.AI_REVIEW_FIELD_ASSIST)
                    .actorAi()
                    .resource("submission", submissionId)
                    .payload("submissionId", submissionId)
                    .payload("taskId", submission.getTaskId())
                    .payload("aiCallId", aiCall.getId())
                    .payload("fieldFindingCount", fieldFindings.size())
                    .payload("ledgerEntryIds", ledgerEntries.stream().map(QualityLedgerEntryEntity::getId).toList())
                    .payload("promptVersion", promptVersionLabel)
                    .payload("promptVersionId", promptVersion.getId())
                    .payload("aiReviewRuleId", aiReviewRuleId)
                    .payload("providerAdapterVersion", providerAdapterVersion)
                    .payload("provider", aiProvider.providerName())
                    .payload("model", aiProvider.modelName())
                    .payload("idempotencyKey", idempotencyKey)
            );
        }
        ledgerService.appendAiOverallRecommendation(
            submissionId,
            submission.getTaskId(),
            aiCall.getId(),
            aiOverallRecommendationPayload(result)
        );
        aiCall.setOutputHash(outputHash);
        return new AiReviewResultView(aiCall, result, rows, false);
    }

    public SubmissionAiProvenanceView getProvenance(Long submissionId, Long requesterUserId) {
        return getProvenance(submissionId, requesterUserId, Set.of());
    }

    public SubmissionAiProvenanceView getProvenance(Long submissionId, Long requesterUserId, Set<String> requesterRoles) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        boolean isOwner = task != null && Objects.equals(task.getOwnerId(), requesterUserId);
        boolean isLabeler = Objects.equals(submission.getLabelerId(), requesterUserId);
        boolean isReviewer = hasRole(requesterRoles, "REVIEWER") || hasRole(requesterRoles, "SENIOR_REVIEWER");
        if (!isOwner && !isLabeler && !isReviewer) {
            throw new SubmissionNotFoundException(submissionId);
        }
        List<AiCallEntity> aiCalls = aiCallMapper.selectBySubmissionId(submissionId);
        for (AiCallEntity aiCall : aiCalls) {
            aiCall.setOutputHash(hash(aiCall.getResponsePayload()));
        }
        return new SubmissionAiProvenanceView(
            submissionId,
            aiCalls,
            aiCallInFieldMapper.selectBySubmissionId(submissionId),
            isOwner || isReviewer
        );
    }

    private boolean hasRole(Set<String> roles, String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }

    public InternalAiReviewContextView getInternalContext(Long submissionId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        if (task == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(submission.getSchemaVersionId());
        DatasetItemEntity datasetItem = datasetItemMapper.selectById(submission.getDatasetItemId());
        AiReviewRuleEntity activeRule = activeReviewRule(task);
        Long aiReviewRuleId = activeRule == null ? null : activeRule.getId();
        PromptVersionEntity promptVersion = activeRule == null
            ? promptVersionService.resolveDefault()
            : promptVersionService.findById(activeRule.getCurrentPromptVersionId());
        if (promptVersion == null) {
            throw new PromptVersionNotFoundException(activeRule == null ? null : activeRule.getCurrentPromptVersionId());
        }
        Map<String, Object> input = buildInput(submission, schemaVersion, datasetItem, task);
        String businessPrompt = promptVersion.getContent();
        List<String> dimensions = dimensionsOf(activeRule);
        ScoredAiReview contextScore = score(emptyResult(input), activeRule);
        return new InternalAiReviewContextView(
            submissionId,
            aiReviewIdempotencyKey(submissionId, promptVersion.getId(), aiReviewRuleId),
            promptVersionLabel(promptVersion),
            promptVersion.getId(),
            aiReviewRuleId,
            PROVIDER_ADAPTER_VERSION,
            input,
            hash(input),
            dimensions,
            contextScore.passThreshold(),
            contextScore.rejectThreshold(),
            contextScore.scoringRuleVersion(),
            businessPrompt,
            PromptTemplate.build(businessPrompt, input, objectMapper)
        );
    }

    @Transactional
    public AiReviewResultView recordInternalResult(InternalAiReviewResultCommand command) {
        SubmissionEntity submission = submissionMapper.selectById(command.submissionId());
        if (submission == null) {
            throw new SubmissionNotFoundException(command.submissionId());
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        if (task == null) {
            throw new SubmissionNotFoundException(command.submissionId());
        }
        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(submission.getSchemaVersionId());
        DatasetItemEntity datasetItem = datasetItemMapper.selectById(submission.getDatasetItemId());
        AiReviewRuleEntity activeRule = activeReviewRule(task);
        Long aiReviewRuleId = activeRule == null ? null : activeRule.getId();
        PromptVersionEntity promptVersion = activeRule == null
            ? promptVersionService.resolveDefault()
            : promptVersionService.findById(activeRule.getCurrentPromptVersionId());
        if (promptVersion == null) {
            throw new PromptVersionNotFoundException(activeRule == null ? null : activeRule.getCurrentPromptVersionId());
        }
        Map<String, Object> input = buildInput(submission, schemaVersion, datasetItem, task);
        String inputHash = hash(input);
        AiCallEntity existing = aiCallMapper.selectByIdempotencyKey(command.idempotencyKey());
        if (existing != null) {
            if (Objects.equals(existing.getInputHash(), inputHash)) {
                existing.setOutputHash(hash(existing.getResponsePayload()));
                List<AiCallInFieldEntity> rows =
                    aiCallInFieldMapper.selectBySubmissionAndAiCall(command.submissionId(), existing.getId());
                return new AiReviewResultView(existing, reconstructResult(existing), rows, true);
            }
            throw new AiInputHashMismatchException(command.submissionId(), command.idempotencyKey());
        }

        String businessPrompt = promptVersion.getContent();
        AiCallResult result = internalCommandResult(command, activeRule);
        Map<String, Object> responsePayload = persistedJsonShape(result.output());
        LocalDateTime now = LocalDateTime.now(clock);
        AiCallEntity aiCall = new AiCallEntity();
        aiCall.setSubmissionId(command.submissionId());
        aiCall.setPurpose("submission_review");
        aiCall.setPromptVersion(promptVersionLabel(promptVersion));
        aiCall.setPromptVersionId(promptVersion.getId());
        aiCall.setAiReviewRuleId(aiReviewRuleId);
        aiCall.setProviderAdapterVersion(PROVIDER_ADAPTER_VERSION);
        aiCall.setModelProvider(command.modelProvider() == null ? "agent" : command.modelProvider());
        aiCall.setModelName(command.modelName() == null ? "unreported" : command.modelName());
        aiCall.setInputHash(inputHash);
        aiCall.setRequestPayload(aiRequestPayload(
            businessPrompt,
            PromptTemplate.build(businessPrompt, input, objectMapper),
            input,
            result.output()
        ));
        aiCall.setResponsePayload(responsePayload);
        aiCall.setScores(normalizedScoreMap(result));
        aiCall.setVerdict(result.overallSuggestion());
        aiCall.setTokenInput(command.tokenInput() == null ? 0 : command.tokenInput());
        aiCall.setTokenOutput(command.tokenOutput() == null ? 0 : command.tokenOutput());
        aiCall.setCostDecimal(costCalculator.computeCost(aiCall.getModelName(), command.usage()));
        aiCall.setPromptTokens(command.usage() == null ? null : command.usage().promptTokens());
        aiCall.setCompletionTokens(command.usage() == null ? null : command.usage().completionTokens());
        aiCall.setTotalTokens(command.usage() == null ? null : command.usage().totalTokens());
        aiCall.setCacheHitTokens(command.usage() == null ? null : command.usage().cacheHitTokens());
        aiCall.setLatencyMs(command.latencyMs() == null ? 0 : command.latencyMs());
        aiCall.setStatus(AiCallStatusCodes.COMPLETED);
        aiCall.setIdempotencyKey(command.idempotencyKey());
        aiCall.setCreatedAt(now);
        aiCall.setCompletedAt(now);
        requireOneRow(aiCallMapper.insert(aiCall), "insert ai_call");
        List<AiCallInFieldEntity> rows = appendFieldRows(command.submissionId(), aiCall.getId(), result.fieldFindings(), now);
        appendAiEvidenceLedger(command.submissionId(), submission.getTaskId(), aiCall.getId(), result);
        aiCall.setOutputHash(hash(responsePayload));
        return new AiReviewResultView(aiCall, result, rows, false);
    }

    private ProviderInvocationResult invokeProvider(
        Long submissionId,
        String promptVersionLabel,
        String businessPrompt,
        Long promptVersionId,
        Long aiReviewRuleId,
        String providerAdapterVersion,
        Map<String, Object> input,
        String inputHash,
        String idempotencyKey
    ) {
        try {
            return retryPolicy.invokeWithRetry(
                () -> aiProvider.invokeWithUsage(
                    new AiCallRequest(promptVersionLabel, businessPrompt, input, aiProvider.timeout())
                ),
                (attemptNumber, exception, willRetry) -> {
                    failedCallRecorder.recordFailedAttempt(
                        submissionId,
                        idempotencyKey,
                        attemptNumber,
                        promptVersionLabel,
                        promptVersionId,
                        aiReviewRuleId,
                        providerAdapterVersion,
                        aiProvider.providerName(),
                        aiProvider.modelName(),
                        inputHash,
                        input,
                        exception
                    );
                    recordRetryAttempt(willRetry);
                }
            );
        } catch (AiProviderException exception) {
            throw new AiProviderFailureException("AI provider invocation failed", exception);
        }
    }

    private AiCallResult normalizedResult(AiCallResult result, AiReviewRuleEntity activeRule) {
        ScoredAiReview score = score(result, activeRule);
        Map<String, Object> output = new LinkedHashMap<>(result.output());
        output.put("overallSuggestion", score.recommendation());
        output.put("confidence", score.finalScore());
        output.put("finalScore", score.finalScore());
        output.put("dimensionScores", dimensionScoreMaps(score.dimensionScores()));
        output.put("threshold", score.threshold());
        output.put("rejectFloor", score.rejectFloor());
        output.put("passThreshold", score.passThreshold());
        output.put("rejectThreshold", score.rejectThreshold());
        output.put("scoringRuleVersion", score.scoringRuleVersion());
        return new AiCallResult(
            output,
            score.recommendation(),
            score.finalScore(),
            result.summary(),
            result.fieldFindings(),
            result.tokenInput(),
            result.tokenOutput(),
            result.cost(),
            result.latencyMs(),
            result.rawResponse()
        );
    }

    private AiCallResult internalCommandResult(InternalAiReviewResultCommand command, AiReviewRuleEntity activeRule) {
        List<FieldFinding> findings = command.fieldFindings() == null ? List.of() : command.fieldFindings();
        Map<String, Object> output = command.responsePayload() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(command.responsePayload());
        output.put("confidence", command.finalScore());
        output.put("finalScore", command.finalScore());
        output.put("summary", command.summary());
        output.put("dimensionScores", dimensionScoreMaps(command.dimensionScores()));
        output.put("fieldFindings", findings.stream().map(this::fieldFindingMap).toList());
        AiCallResult agentRawResult = new AiCallResult(
            output,
            command.recommendation(),
            command.finalScore(),
            command.summary(),
            findings,
            command.tokenInput() == null ? 0 : command.tokenInput(),
            command.tokenOutput() == null ? 0 : command.tokenOutput(),
            BigDecimal.ZERO,
            command.latencyMs() == null ? 0 : command.latencyMs(),
            command.rawResponse()
        );
        return normalizedResult(agentRawResult, activeRule);
    }

    private Map<String, Object> aiRequestPayload(
        String businessPrompt,
        String renderedPrompt,
        Map<String, Object> input,
        Map<String, Object> normalizedOutput
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("businessPrompt", businessPrompt);
        payload.put("renderedPrompt", renderedPrompt);
        payload.put("input", input);
        payload.put("normalizedOutputShape", normalizedOutput);
        return payload;
    }

    private Map<String, Object> aiOverallRecommendationPayload(AiCallResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recommendation", result.overallSuggestion());
        payload.put("finalScore", result.confidence());
        payload.put("threshold", result.output().get("threshold"));
        payload.put("rejectFloor", result.output().get("rejectFloor"));
        payload.put("passThreshold", result.output().get("passThreshold"));
        payload.put("rejectThreshold", result.output().get("rejectThreshold"));
        payload.put("scoringRuleVersion", result.output().get("scoringRuleVersion"));
        payload.put("dimensionScores", result.output().getOrDefault("dimensionScores", List.of()));
        payload.put("summary", result.summary());
        return payload;
    }

    private void appendAiEvidenceLedger(Long submissionId, Long taskId, Long aiCallId, AiCallResult result) {
        List<FieldFinding> fieldFindings = result.fieldFindings() == null ? List.of() : result.fieldFindings();
        if (!fieldFindings.isEmpty()) {
            ledgerService.appendAiFieldFindings(submissionId, taskId, aiCallId, fieldFindings);
        }
        ledgerService.appendAiOverallRecommendation(submissionId, taskId, aiCallId, aiOverallRecommendationPayload(result));
    }

    private List<AiCallInFieldEntity> appendFieldRows(
        Long submissionId,
        Long aiCallId,
        List<FieldFinding> fieldFindings,
        LocalDateTime now
    ) {
        List<AiCallInFieldEntity> rows = new ArrayList<>();
        for (FieldFinding finding : fieldFindings == null ? List.<FieldFinding>of() : fieldFindings) {
            AiCallInFieldEntity row = fieldRow(submissionId, aiCallId, finding.fieldPath(), now);
            requireOneRow(aiCallInFieldMapper.insert(row), "insert ai_call_in_field");
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> scoreMap(List<DimensionScoreValue> dimensionScores, BigDecimal finalScore) {
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("finalScore", finalScore);
        scores.put("dimensionScores", dimensionScoreMaps(dimensionScores));
        return scores;
    }

    private Map<String, Object> normalizedScoreMap(AiCallResult result) {
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("finalScore", result.confidence());
        scores.put("dimensionScores", result.output().getOrDefault("dimensionScores", List.of()));
        return scores;
    }

    private List<Map<String, Object>> dimensionScoreMaps(List<DimensionScoreValue> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("dimension", value.dimension());
            map.put("score", value.score());
            if (value.reason() != null) {
                map.put("reason", value.reason());
            }
            return map;
        }).toList();
    }

    private Map<String, Object> fieldFindingMap(FieldFinding finding) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldPath", finding.fieldPath());
        if (finding.stableId() != null) {
            map.put("stableId", finding.stableId());
        }
        if (finding.label() != null) {
            map.put("label", finding.label());
        }
        map.put("severity", finding.severity());
        map.put("finding", finding.finding());
        if (finding.confidence() != null) {
            map.put("confidence", finding.confidence());
        }
        return map;
    }

    private void recordRetryAttempt(boolean willRetry) {
        if (willRetry) {
            metrics.recordRetryAttempt(aiProvider.providerName());
        }
    }

    private Map<String, Object> providerError(AiProviderFailureException exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", exception.getMessage());
        if (exception.getCause() instanceof AiProviderException cause) {
            payload.put("providerCode", cause.getProviderCode());
            payload.put("statusCode", cause.getStatusCode());
            payload.put("retryable", cause.isRetryable());
        }
        return payload;
    }

    private Map<String, Object> buildInput(
        SubmissionEntity submission,
        SchemaVersionEntity schemaVersion,
        DatasetItemEntity datasetItem,
        TaskEntity task
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("schemaVersionId", schemaVersion.getId());
        input.put("schemaFields", schemaRuntimeAdapter.fieldMapsForAi(schemaVersion.getSchemaJson()));
        input.put("answerPayload", nullToEmpty(submission.getAnswerPayload()));
        input.put("datasetItemPayload", datasetItem == null ? Map.of() : nullToEmpty(datasetItem.getItemPayload()));
        input.put("task", taskInput(task));
        input.put("submission", submissionInput(submission));
        return input;
    }

    private Map<String, Object> taskInput(TaskEntity task) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", task.getId());
        value.put("title", task.getTitle());
        value.put("description", task.getDescription());
        return value;
    }

    private Map<String, Object> submissionInput(SubmissionEntity submission) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", submission.getId());
        value.put("createdAt", submission.getCreatedAt() == null ? null : submission.getCreatedAt().toString());
        return value;
    }

    private AiCallInFieldEntity fieldRow(Long submissionId, Long aiCallId, String fieldPath, LocalDateTime now) {
        Integer maxOrdinal = aiCallInFieldMapper.selectMaxOrdinal(submissionId, fieldPath);
        AiCallInFieldEntity entity = new AiCallInFieldEntity();
        entity.setSubmissionId(submissionId);
        entity.setFieldPath(fieldPath);
        entity.setAiCallId(aiCallId);
        entity.setAccepted(false);
        entity.setUserModifiedAfter(false);
        entity.setOrdinal(maxOrdinal == null ? 1 : maxOrdinal + 1);
        entity.setCreatedAt(now);
        return entity;
    }

    private AiCallResult reconstructResult(AiCallEntity aiCall) {
        Map<String, Object> output = nullToEmpty(aiCall.getResponsePayload());
        return new AiCallResult(
            output,
            stringValue(output.get("overallSuggestion")),
            bigDecimalValue(output.get("confidence")),
            stringValue(output.get("summary")),
            fieldFindingsOf(output.get("fieldFindings")),
            aiCall.getTokenInput() == null ? 0 : aiCall.getTokenInput(),
            aiCall.getTokenOutput() == null ? 0 : aiCall.getTokenOutput(),
            aiCall.getCostDecimal() == null ? BigDecimal.ZERO : aiCall.getCostDecimal(),
            aiCall.getLatencyMs() == null ? 0 : aiCall.getLatencyMs(),
            null
        );
    }

    private List<FieldFinding> fieldFindingsOf(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<FieldFinding> findings = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                findings.add(new FieldFinding(
                    stringValue(map.get("fieldPath")),
                    stringValue(map.get("stableId")),
                    stringValue(map.get("label")),
                    stringValue(map.get("severity")),
                    stringValue(map.get("finding")),
                    bigDecimalValue(map.get("confidence"))
                ));
            }
        }
        return findings;
    }

    private AiReviewRuleEntity activeReviewRule(TaskEntity task) {
        Long ruleId = task.getCurrentAiReviewRuleId();
        if (ruleId == null) {
            return null;
        }
        AiReviewRuleEntity rule = aiReviewRuleMapper.selectById(ruleId);
        if (rule == null || !Objects.equals(rule.getTaskId(), task.getId())) {
            throw new AiReviewRuleNotFoundException(ruleId);
        }
        return rule;
    }

    private List<String> dimensionsOf(AiReviewRuleEntity rule) {
        if (rule == null || rule.getDimensionsJson() == null || rule.getDimensionsJson().isBlank()) {
            return List.of("overall");
        }
        try {
            List<?> raw = objectMapper.readValue(rule.getDimensionsJson(), List.class);
            List<String> dimensions = raw.stream()
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .toList();
            return dimensions.isEmpty() ? List.of("overall") : dimensions;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to parse AI review dimensions", exception);
        }
    }

    private BigDecimal thresholdOf(AiReviewRuleEntity rule) {
        return score(emptyResult(Map.of()), rule).threshold();
    }

    private ScoredAiReview score(AiCallResult result, AiReviewRuleEntity rule) {
        return scoringPolicy.score(result, dimensionsOf(rule), passThresholdOf(rule), rejectThresholdOf(rule));
    }

    private BigDecimal passThresholdOf(AiReviewRuleEntity rule) {
        if (rule == null) {
            return null;
        }
        if (rule.getPassThreshold() != null) {
            return rule.getPassThreshold();
        }
        return rule.getThreshold();
    }

    private BigDecimal rejectThresholdOf(AiReviewRuleEntity rule) {
        if (rule == null) {
            return null;
        }
        return rule.getRejectThreshold();
    }

    // Single idempotency-key generator shared by the owner (synchronous) and agent (async outbox)
    // review paths. It is intentionally provider/model-agnostic: the agent resolves its provider at
    // runtime, so the key it can compute at enqueue time cannot depend on the provider. Keying both
    // paths the same way ensures one submission+prompt(+rule) yields a single ai_call/ledger entry.
    private String aiReviewIdempotencyKey(Long submissionId, Long promptVersionId, Long aiReviewRuleId) {
        String key = "submission:%d:ai_review:promptVersionId:%d:adapter:%s".formatted(
            submissionId,
            promptVersionId,
            PROVIDER_ADAPTER_VERSION
        );
        if (aiReviewRuleId != null) {
            key = key + ":ruleVersionId:" + aiReviewRuleId;
        }
        if (key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "ai_calls.idempotency_key would exceed " + IDEMPOTENCY_KEY_MAX_LENGTH + " characters"
            );
        }
        return key;
    }

    private AiCallResult emptyResult(Map<String, Object> input) {
        return new AiCallResult(
            Map.of("inputHash", hash(input)),
            "manual_review",
            BigDecimal.ZERO,
            "",
            List.of(),
            0,
            0,
            BigDecimal.ZERO,
            0,
            null
        );
    }

    private String promptVersionLabel(PromptVersionEntity promptVersion) {
        return "promptVersion#" + promptVersion.getVersionNumber();
    }

    private String hash(Object value) {
        return canonicalizer.sha256Hex(canonicalizer.canonicalJson(value));
    }

    private Map<String, Object> persistedJsonShape(Map<String, Object> value) {
        try {
            return objectMapper.readValue(
                objectMapper.writeValueAsString(value),
                new TypeReference<Map<String, Object>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to normalize AI response payload", exception);
        }
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal bigDecimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private void requireOneRow(int rows, String action) {
        if (rows != 1) {
            throw new IllegalStateException("Expected one row to " + action + ", got " + rows);
        }
    }
}
