package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.exception.AiInputHashMismatchException;
import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.exception.AiProviderFailureException;
import com.labelhub.api.module.ai.mapper.AiCallInFieldMapper;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.provider.AiCallRequest;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.AiProvider;
import com.labelhub.api.module.ai.provider.FieldFinding;
import com.labelhub.api.module.ai.provider.ProviderInvocationResult;
import com.labelhub.api.module.ai.service.view.AiReviewResultView;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.quality.service.LedgerService;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReviewService {

    private final SubmissionMapper submissionMapper;
    private final SchemaVersionMapper schemaVersionMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final TaskMapper taskMapper;
    private final AiCallMapper aiCallMapper;
    private final AiCallInFieldMapper aiCallInFieldMapper;
    private final LedgerService ledgerService;
    private final Canonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AiProvider aiProvider;

    public AiReviewService(
        SubmissionMapper submissionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DatasetItemMapper datasetItemMapper,
        TaskMapper taskMapper,
        AiCallMapper aiCallMapper,
        AiCallInFieldMapper aiCallInFieldMapper,
        LedgerService ledgerService,
        Canonicalizer canonicalizer,
        ObjectMapper objectMapper,
        Clock clock,
        AiProvider aiProvider
    ) {
        this.submissionMapper = submissionMapper;
        this.schemaVersionMapper = schemaVersionMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.taskMapper = taskMapper;
        this.aiCallMapper = aiCallMapper;
        this.aiCallInFieldMapper = aiCallInFieldMapper;
        this.ledgerService = ledgerService;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.aiProvider = aiProvider;
    }

    @Transactional
    public AiReviewResultView review(Long submissionId, Long ownerId, String promptVersion) {
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
        Map<String, Object> input = buildInput(submission, schemaVersion, datasetItem, task);
        String inputHash = hash(input);
        String idempotencyKey = idempotencyKey(submissionId, promptVersion);

        AiCallEntity existing = aiCallMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (Objects.equals(existing.getInputHash(), inputHash)) {
                existing.setOutputHash(hash(existing.getResponsePayload()));
                List<AiCallInFieldEntity> rows =
                    aiCallInFieldMapper.selectBySubmissionAndAiCall(submissionId, existing.getId());
                return new AiReviewResultView(existing, reconstructResult(existing), rows, true);
            }
            throw new AiInputHashMismatchException(submissionId, idempotencyKey);
        }

        ProviderInvocationResult invocation = invokeProvider(promptVersion, input);
        AiCallResult result = invocation.result();
        AiCallUsage usage = invocation.usage();
        Map<String, Object> responsePayload = persistedJsonShape(result.output());
        String outputHash = hash(responsePayload);
        LocalDateTime now = LocalDateTime.now(clock);
        AiCallEntity aiCall = new AiCallEntity();
        aiCall.setSubmissionId(submissionId);
        aiCall.setPurpose("submission_review");
        aiCall.setPromptVersion(promptVersion);
        aiCall.setModelProvider(aiProvider.providerName());
        aiCall.setModelName(aiProvider.modelName());
        aiCall.setInputHash(inputHash);
        aiCall.setRequestPayload(input);
        aiCall.setResponsePayload(responsePayload);
        aiCall.setTokenInput(result.tokenInput());
        aiCall.setTokenOutput(result.tokenOutput());
        aiCall.setCostDecimal(result.cost());
        aiCall.setPromptTokens(usage == null ? null : usage.promptTokens());
        aiCall.setCompletionTokens(usage == null ? null : usage.completionTokens());
        aiCall.setTotalTokens(usage == null ? null : usage.totalTokens());
        aiCall.setCacheHitTokens(usage == null ? null : usage.cacheHitTokens());
        aiCall.setLatencyMs(Math.toIntExact(result.latencyMs()));
        aiCall.setStatus("completed");
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
            ledgerService.appendAiFieldFindings(submissionId, submission.getTaskId(), aiCall.getId(), fieldFindings);
        }
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
        boolean isReviewer = requesterRoles != null && requesterRoles.contains("REVIEWER");
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
            aiCallInFieldMapper.selectBySubmissionId(submissionId)
        );
    }

    private ProviderInvocationResult invokeProvider(String promptVersion, Map<String, Object> input) {
        try {
            return aiProvider.invokeWithUsage(new AiCallRequest(promptVersion, input, Duration.ofSeconds(30)));
        } catch (AiProviderException exception) {
            throw new AiProviderFailureException("AI provider invocation failed", exception);
        }
    }

    private Map<String, Object> buildInput(
        SubmissionEntity submission,
        SchemaVersionEntity schemaVersion,
        DatasetItemEntity datasetItem,
        TaskEntity task
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("schemaVersionId", schemaVersion.getId());
        input.put("schemaFields", schemaVersion.getSchemaJson().getOrDefault("fields", List.of()));
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

    private String idempotencyKey(Long submissionId, String promptVersion) {
        return "submission:%d:provider:%s:model:%s:prompt:%s".formatted(
            submissionId,
            aiProvider.providerName(),
            aiProvider.modelName(),
            promptVersion
        );
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
