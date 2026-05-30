package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.FieldAITrace;
import com.labelhub.api.generated.model.FieldAssistRequest;
import com.labelhub.api.generated.model.FieldAssistResponse;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallStatusCodes;
import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.exception.AiProviderFailureException;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.provider.AiCallRequest;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.AiProvider;
import com.labelhub.api.module.ai.provider.PromptTemplate;
import com.labelhub.api.module.ai.provider.ProviderInvocationResult;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.runtime.SchemaRuntimeAdapter;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.service.SessionService;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FieldAssistService {

    private static final String PROMPT_VERSION = "field-assist-v1";
    private static final String PROVIDER_ADAPTER_VERSION = AiReviewService.PROVIDER_ADAPTER_VERSION;

    private final SessionService sessionService;
    private final SchemaVersionMapper schemaVersionMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final TaskMapper taskMapper;
    private final AiCallMapper aiCallMapper;
    private final AiProvider aiProvider;
    private final AiCallCostCalculator costCalculator;
    private final Canonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SchemaRuntimeAdapter schemaRuntimeAdapter;

    public FieldAssistService(
        SessionService sessionService,
        SchemaVersionMapper schemaVersionMapper,
        DatasetItemMapper datasetItemMapper,
        TaskMapper taskMapper,
        AiCallMapper aiCallMapper,
        AiProvider aiProvider,
        AiCallCostCalculator costCalculator,
        Canonicalizer canonicalizer,
        ObjectMapper objectMapper,
        Clock clock,
        SchemaRuntimeAdapter schemaRuntimeAdapter
    ) {
        this.sessionService = sessionService;
        this.schemaVersionMapper = schemaVersionMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.taskMapper = taskMapper;
        this.aiCallMapper = aiCallMapper;
        this.aiProvider = aiProvider;
        this.costCalculator = costCalculator;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.schemaRuntimeAdapter = schemaRuntimeAdapter;
    }

    @Transactional
    public FieldAssistResponse assist(FieldAssistRequest request, Long labelerId) {
        SessionEntity session = sessionService.assertLabelerOwnsSession(request.getSessionId(), labelerId);
        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(session.getSchemaVersionId());
        DatasetItemEntity datasetItem = datasetItemMapper.selectById(session.getDatasetItemId());
        TaskEntity task = taskMapper.selectById(session.getTaskId());
        String fieldPath = request.getFieldPath();
        Map<String, Object> input = buildInput(request, session, schemaVersion, datasetItem, task);
        String inputHash = hash(input);
        String idempotencyKey = idempotencyKey(session.getId(), fieldPath, inputHash);
        AiCallEntity existing = aiCallMapper.selectByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return response(existing, fieldPath, nullToEmpty(existing.getResponsePayload()));
        }

        String businessPrompt = businessPrompt(request);
        ProviderInvocationResult invocation = invokeProvider(businessPrompt, input);
        AiCallResult result = invocation.result();
        Map<String, Object> responsePayload = persistedJsonShape(result.output());
        AiCallUsage usage = invocation.usage();
        LocalDateTime now = LocalDateTime.now(clock);
        AiCallEntity aiCall = new AiCallEntity();
        aiCall.setFieldPath(fieldPath);
        aiCall.setPurpose("field_assist");
        aiCall.setPromptVersion(promptVersionLabel(request));
        aiCall.setProviderAdapterVersion(PROVIDER_ADAPTER_VERSION);
        aiCall.setModelProvider(aiProvider.providerName());
        aiCall.setModelName(aiProvider.modelName());
        aiCall.setInputHash(inputHash);
        aiCall.setRequestPayload(aiRequestPayload(businessPrompt, PromptTemplate.build(businessPrompt, input, objectMapper), input, responsePayload));
        aiCall.setResponsePayload(responsePayload);
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("confidence", result.confidence());
        scores.put("dimensionScores", responsePayload.getOrDefault("dimensionScores", List.of()));
        aiCall.setScores(scores);
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
        requireOneRow(aiCallMapper.insert(aiCall), "insert field assist ai_call");
        return response(aiCall, fieldPath, responsePayload);
    }

    private ProviderInvocationResult invokeProvider(String businessPrompt, Map<String, Object> input) {
        try {
            return aiProvider.invokeWithUsage(new AiCallRequest(PROMPT_VERSION, businessPrompt, input, aiProvider.timeout()));
        } catch (AiProviderException exception) {
            throw new AiProviderFailureException("AI provider invocation failed", exception);
        }
    }

    private Map<String, Object> buildInput(
        FieldAssistRequest request,
        SessionEntity session,
        SchemaVersionEntity schemaVersion,
        DatasetItemEntity datasetItem,
        TaskEntity task
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("sessionId", session.getId());
        input.put("fieldPath", request.getFieldPath());
        input.put("fieldInput", nullToEmpty(request.getInput()));
        input.put("schemaVersionId", session.getSchemaVersionId());
        input.put("schemaFields", schemaVersion == null ? List.of() : schemaRuntimeAdapter.fieldMapsForAi(schemaVersion.getSchemaJson()));
        input.put("datasetItemPayload", datasetItem == null ? Map.of() : nullToEmpty(datasetItem.getItemPayload()));
        input.put("task", task == null ? Map.of() : taskInput(task));
        return input;
    }

    private Map<String, Object> taskInput(TaskEntity task) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", task.getId());
        value.put("title", task.getTitle());
        value.put("description", task.getDescription());
        return value;
    }

    private String businessPrompt(FieldAssistRequest request) {
        String custom = request.getPromptVersion();
        String prompt = """
            Provide field-level assistance for one labeling answer. Return structured evidence only.
            Do not make final dataset verdicts or bypass human review. Focus on the requested fieldPath.
            """;
        if (custom != null && !custom.isBlank()) {
            prompt = prompt + "\nRequested prompt version/source: " + custom;
        }
        return prompt;
    }

    private FieldAssistResponse response(AiCallEntity aiCall, String fieldPath, Map<String, Object> output) {
        FieldAITrace trace = new FieldAITrace()
            .aiCallId(aiCall.getId())
            .fieldPath(fieldPath)
            .source(aiCall.getPromptVersion())
            .confidence(aiCall.getScores() == null ? null : bigDecimalValue(aiCall.getScores().get("confidence")))
            .accepted(false)
            .userModifiedAfter(false)
            .ordinal(1);
        return new FieldAssistResponse()
            .aiCallId(aiCall.getId())
            .fieldPath(fieldPath)
            .output(output)
            .provenance(trace);
    }

    private String promptVersionLabel(FieldAssistRequest request) {
        return request.getPromptVersion() == null || request.getPromptVersion().isBlank()
            ? PROMPT_VERSION
            : request.getPromptVersion();
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

    private String idempotencyKey(Long sessionId, String fieldPath, String inputHash) {
        String key = "fieldAssist:s:%d:f:%s:p:%s:m:%s:h:%s".formatted(
            sessionId,
            hash(Map.of("fieldPath", fieldPath == null ? "" : fieldPath)).substring(0, 16),
            aiProvider.providerName(),
            aiProvider.modelName(),
            inputHash.substring(0, 16)
        );
        if (key.length() > 160) {
            throw new IllegalArgumentException("ai_calls.idempotency_key would exceed 160 characters");
        }
        return key;
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

    private java.math.BigDecimal bigDecimalValue(Object value) {
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new java.math.BigDecimal(number.toString());
        }
        return value == null ? null : new java.math.BigDecimal(String.valueOf(value));
    }

    private void requireOneRow(int rows, String action) {
        if (rows != 1) {
            throw new IllegalStateException("Expected one row to " + action + ", got " + rows);
        }
    }
}
