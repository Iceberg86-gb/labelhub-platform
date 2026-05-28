package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallStatusCodes;
import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FailedAiCallRecorder {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 160;

    private final AiCallMapper aiCallMapper;
    private final Clock clock;
    private final AuditLogService auditLogService;

    @Autowired
    public FailedAiCallRecorder(AiCallMapper aiCallMapper, Clock clock, AuditLogService auditLogService) {
        this.aiCallMapper = aiCallMapper;
        this.clock = clock;
        this.auditLogService = auditLogService;
    }

    public FailedAiCallRecorder(AiCallMapper aiCallMapper, Clock clock) {
        this(aiCallMapper, clock, AuditLogService.noop());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(
        Long submissionId,
        String canonicalIdempotencyKey,
        int attemptNumber,
        String promptVersion,
        Long promptVersionId,
        String providerAdapterVersion,
        String providerName,
        String modelName,
        String inputHash,
        Map<String, Object> requestPayload,
        AiProviderException exception
    ) {
        String failedAttemptKey = failedAttemptKey(canonicalIdempotencyKey, attemptNumber);
        LocalDateTime now = LocalDateTime.now(clock);
        AiCallEntity failed = new AiCallEntity();
        failed.setSubmissionId(submissionId);
        failed.setPurpose("submission_review");
        failed.setPromptVersion(promptVersion);
        failed.setPromptVersionId(promptVersionId);
        failed.setProviderAdapterVersion(providerAdapterVersion);
        failed.setModelProvider(providerName);
        failed.setModelName(modelName);
        failed.setInputHash(inputHash);
        failed.setRequestPayload(requestPayload);
        failed.setResponsePayload(failurePayload(exception));
        failed.setTokenInput(0);
        failed.setTokenOutput(0);
        failed.setCostDecimal(new BigDecimal("0.000000"));
        failed.setPromptTokens(null);
        failed.setCompletionTokens(null);
        failed.setTotalTokens(null);
        failed.setCacheHitTokens(null);
        failed.setLatencyMs(0);
        failed.setStatus(AiCallStatusCodes.FAILED);
        failed.setIdempotencyKey(failedAttemptKey);
        failed.setCreatedAt(now);
        failed.setCompletedAt(now);
        aiCallMapper.insert(failed);
        auditLogService.record(
            AuditEventBuilder.forAction(AuditActions.AI_REVIEW_RECORDED_FAILED_CALL)
                .actorSystem()
                .resource("ai_call", failed.getId())
                .payload("aiCallId", failed.getId())
                .payload("submissionId", submissionId)
                .payload("attemptNumber", attemptNumber)
                .payload("promptVersion", promptVersion)
                .payload("promptVersionId", promptVersionId)
                .payload("providerAdapterVersion", providerAdapterVersion)
                .payload("provider", providerName)
                .payload("model", modelName)
                .payload("inputHash", inputHash)
                .payload("idempotencyKey", failedAttemptKey)
                .payload("status", AiCallStatusCodes.FAILED)
                .payload("failure", failurePayload(exception))
        );
    }

    private String failedAttemptKey(String canonicalIdempotencyKey, int attemptNumber) {
        String key = canonicalIdempotencyKey + "#failed-attempt-" + attemptNumber;
        if (key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                "ai_calls.idempotency_key would exceed " + IDEMPOTENCY_KEY_MAX_LENGTH + " characters"
            );
        }
        return key;
    }

    private Map<String, Object> failurePayload(AiProviderException exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", exception.getMessage());
        payload.put("providerCode", exception.getProviderCode());
        payload.put("statusCode", exception.getStatusCode());
        payload.put("retryable", exception.isRetryable());
        return payload;
    }
}
