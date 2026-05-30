package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEvent;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.entity.AiCallStatusCodes;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.exception.AiInputHashMismatchException;
import com.labelhub.api.module.ai.exception.AiProviderException;
import com.labelhub.api.module.ai.exception.AiProviderFailureException;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.exception.PromptVersionNotFoundException;
import com.labelhub.api.module.ai.mapper.AiCallInFieldMapper;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.observability.AiIdempotencyMetrics;
import com.labelhub.api.module.ai.provider.AiCallRequest;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.AiProvider;
import com.labelhub.api.module.ai.provider.FieldFinding;
import com.labelhub.api.module.ai.provider.OpenAiCompatibleProperties;
import com.labelhub.api.module.ai.provider.ProviderInvocationResult;
import com.labelhub.api.module.ai.service.view.AiReviewResultView;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-25T12:00:00");

    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private final SchemaVersionMapper schemaVersionMapper = mock(SchemaVersionMapper.class);
    private final DatasetItemMapper datasetItemMapper = mock(DatasetItemMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AiCallMapper aiCallMapper = mock(AiCallMapper.class);
    private final AiReviewRuleMapper aiReviewRuleMapper = mock(AiReviewRuleMapper.class);
    private final AiCallInFieldMapper aiCallInFieldMapper = mock(AiCallInFieldMapper.class);
    private final LedgerService ledgerService = mock(LedgerService.class);
    private final PromptVersionService promptVersionService = mock(PromptVersionService.class);
    private final AiProvider aiProvider = mock(AiProvider.class);
    private final AiCallCostCalculator costCalculator = mock(AiCallCostCalculator.class);
    private final AiIdempotencyMetrics metrics = mock(AiIdempotencyMetrics.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Canonicalizer canonicalizer = new Canonicalizer(objectMapper);
    private AiReviewService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);
        OpenAiCompatibleProperties retryProperties = new OpenAiCompatibleProperties(
            "",
            "",
            "mock-v1",
            "mock",
            new BigDecimal("0.001"),
            Duration.ofSeconds(30),
            3,
            Duration.ZERO
        );
        AiRetryPolicy retryPolicy = new AiRetryPolicy(retryProperties, ignored -> {});
        FailedAiCallRecorder failedRecorder = new FailedAiCallRecorder(aiCallMapper, clock);
        service = new AiReviewService(
            submissionMapper,
            schemaVersionMapper,
            datasetItemMapper,
            taskMapper,
            aiCallMapper,
            aiReviewRuleMapper,
            aiCallInFieldMapper,
            ledgerService,
            promptVersionService,
            canonicalizer,
            objectMapper,
            clock,
            aiProvider,
            costCalculator,
            metrics,
            retryPolicy,
            failedRecorder,
            auditLogService
        );
        when(aiProvider.providerName()).thenReturn("mock");
        when(aiProvider.modelName()).thenReturn("mock-v1");
        when(aiProvider.timeout()).thenReturn(Duration.ofSeconds(30));
        when(promptVersionService.findById(1L)).thenReturn(defaultPromptVersion());
        when(promptVersionService.findById(7L)).thenReturn(rulePromptVersion());
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class))).thenAnswer(invocation ->
            new ProviderInvocationResult(aiProvider.invoke(invocation.getArgument(0)), null)
        );
        when(costCalculator.computeCost(any(), any())).thenReturn(new BigDecimal("0.000100"));
        seedOwnedSubmission();
    }

    @Test
    void review_invokes_provider_and_writes_ai_call_with_correct_fields() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        AiReviewResultView result = service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        AiCallEntity inserted = captor.getValue();
        assertThat(inserted.getSubmissionId()).isEqualTo(300L);
        assertThat(inserted.getPurpose()).isEqualTo("submission_review");
        assertThat(inserted.getModelProvider()).isEqualTo("mock");
        assertThat(inserted.getModelName()).isEqualTo("mock-v1");
        assertThat(inserted.getStatus()).isEqualTo(AiCallStatusCodes.COMPLETED);
        assertThat(inserted.getPromptVersion()).isEqualTo("promptVersion#1");
        assertThat(inserted.getPromptVersionId()).isEqualTo(1L);
        assertThat(inserted.getAiReviewRuleId()).isNull();
        assertThat(inserted.getProviderAdapterVersion()).isEqualTo("agent-default-v1");
        assertThat(inserted.getIdempotencyKey()).isEqualTo(
            "submission:300:provider:mock:model:mock-v1:promptVersionId:1:adapter:agent-default-v1"
        );
        assertThat(inserted.getIdempotencyKey().length()).isLessThanOrEqualTo(160);
        assertThat(inserted.getRequestPayload()).doesNotContainKey("labelerId");
        assertThat(inserted.getResponsePayload()).containsEntry("overallSuggestion", "pass");
        assertThat(result.idempotencyHit()).isFalse();
        assertThat(result.aiCall().getOutputHash()).hasSize(64);
    }

    @Test
    void review_readsSchemaFieldsFromJsonSchemaV2RuntimeAdapter() {
        when(schemaVersionMapper.selectById(700L)).thenReturn(jsonSchemaVersion());
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) captor.getValue().getRequestPayload().get("input");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schemaFields = (List<Map<String, Object>>) input.get("schemaFields");
        assertThat(schemaFields).hasSize(1);
        assertThat(schemaFields.get(0)).containsEntry("stableId", "field-title");
    }

    @Test
    void active_task_rule_overrides_request_prompt_and_binds_rule_evidence() {
        TaskEntity task = task(10L, 1001L);
        task.setCurrentAiReviewRuleId(19L);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(aiReviewRuleMapper.selectById(19L)).thenReturn(activeRule(19L, 10L, 7L));
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallRequest> requestCaptor = ArgumentCaptor.forClass(AiCallRequest.class);
        verify(aiProvider).invokeWithUsage(requestCaptor.capture());
        assertThat(requestCaptor.getValue().promptVersion()).isEqualTo("promptVersion#2");

        ArgumentCaptor<AiCallEntity> rowCaptor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(rowCaptor.capture());
        AiCallEntity row = rowCaptor.getValue();
        assertThat(row.getPromptVersion()).isEqualTo("promptVersion#2");
        assertThat(row.getPromptVersionId()).isEqualTo(7L);
        assertThat(row.getAiReviewRuleId()).isEqualTo(19L);
        assertThat(row.getIdempotencyKey()).isEqualTo(
            "submission:300:provider:mock:model:mock-v1:promptVersionId:7:adapter:agent-default-v1:ruleVersionId:19"
        );
        assertThat(row.getIdempotencyKey().length()).isLessThanOrEqualTo(160);
    }

    @Test
    void active_task_rule_missing_fails_without_falling_back_to_request_prompt() {
        TaskEntity task = task(10L, 1001L);
        task.setCurrentAiReviewRuleId(404L);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(aiReviewRuleMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiReviewRuleNotFoundException.class)
            .hasMessageContaining("404");

        verify(promptVersionService, never()).findById(1L);
        verify(aiProvider, never()).invoke(any());
        verify(aiCallMapper, never()).insert(any());
    }

    @Test
    void review_writes_in_order_ai_call_before_fields() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        InOrder inOrder = inOrder(aiCallMapper, aiCallInFieldMapper);
        inOrder.verify(aiCallMapper).insert(any(AiCallEntity.class));
        inOrder.verify(aiCallInFieldMapper).insert(any(AiCallInFieldEntity.class));
    }

    @Test
    void review_writes_ai_call_in_field_per_field_finding() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        AiReviewResultView result = service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallInFieldEntity> captor = ArgumentCaptor.forClass(AiCallInFieldEntity.class);
        verify(aiCallInFieldMapper).insert(captor.capture());
        AiCallInFieldEntity row = captor.getValue();
        assertThat(row.getSubmissionId()).isEqualTo(300L);
        assertThat(row.getAiCallId()).isEqualTo(900L);
        assertThat(row.getFieldPath()).isEqualTo("field-title");
        assertThat(row.getAccepted()).isFalse();
        assertThat(row.getUserModifiedAfter()).isFalse();
        assertThat(result.fieldRows()).hasSize(1);
    }

    @Test
    void review_computes_canonical_input_hash() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        assertThat(captor.getValue().getInputHash()).isEqualTo(inputHashForDefaultFixture());
    }

    @Test
    void review_computes_canonical_output_hash() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        AiReviewResultView result = service.review(300L, 1001L, 1L);

        assertThat(result.aiCall().getOutputHash())
            .isEqualTo(canonicalizer.sha256Hex(canonicalizer.canonicalJson(jsonRoundTrip(result.aiCall().getResponsePayload()))));
    }

    @Test
    void review_computes_output_hash_from_persisted_response_payload_shape() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        AiReviewResultView result = service.review(300L, 1001L, 1L);

        Map<String, Object> persistedShape = jsonRoundTrip(result.aiCall().getResponsePayload());
        assertThat(result.aiCall().getOutputHash())
            .isEqualTo(canonicalizer.sha256Hex(canonicalizer.canonicalJson(persistedShape)));
    }

    @Test
    void review_assigns_ordinal_1_for_first_field_review() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.selectMaxOrdinal(300L, "field-title")).thenReturn(null);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallInFieldEntity> captor = ArgumentCaptor.forClass(AiCallInFieldEntity.class);
        verify(aiCallInFieldMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrdinal()).isEqualTo(1);
    }

    @Test
    void review_increments_ordinal_for_repeated_field_review() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.selectMaxOrdinal(300L, "field-title")).thenReturn(2);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallInFieldEntity> captor = ArgumentCaptor.forClass(AiCallInFieldEntity.class);
        verify(aiCallInFieldMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrdinal()).isEqualTo(3);
    }

    @Test
    void review_marks_status_completed_and_records_completed_at() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AiCallStatusCodes.COMPLETED);
        assertThat(captor.getValue().getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void review_persists_token_usage_when_provider_returns_usage() {
        AiCallUsage usage = new AiCallUsage(101, 52, 153, 30);
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenReturn(new ProviderInvocationResult(providerResult(), usage));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        AiCallEntity inserted = captor.getValue();
        assertThat(inserted.getPromptTokens()).isEqualTo(101);
        assertThat(inserted.getCompletionTokens()).isEqualTo(52);
        assertThat(inserted.getTotalTokens()).isEqualTo(153);
        assertThat(inserted.getCacheHitTokens()).isEqualTo(30);
    }

    @Test
    void review_persists_null_tokens_when_provider_omits_usage() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenReturn(new ProviderInvocationResult(providerResult(), null));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        AiCallEntity inserted = captor.getValue();
        assertThat(inserted.getPromptTokens()).isNull();
        assertThat(inserted.getCompletionTokens()).isNull();
        assertThat(inserted.getTotalTokens()).isNull();
        assertThat(inserted.getCacheHitTokens()).isNull();
    }

    @Test
    void review_falls_back_to_fixed_estimate_when_usage_incomplete() {
        AiCallUsage usage = new AiCallUsage(101, null, null, null);
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenReturn(new ProviderInvocationResult(providerResult(), usage));
        when(costCalculator.computeCost("mock-v1", usage)).thenReturn(new BigDecimal("0.001000"));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        assertThat(captor.getValue().getCostDecimal()).isEqualByComparingTo("0.001000");
        verify(costCalculator).computeCost("mock-v1", usage);
    }

    @Test
    void review_uses_calculator_cost_when_usage_present() {
        AiCallUsage usage = new AiCallUsage(1000, 500, 1500, null);
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenReturn(new ProviderInvocationResult(providerResult(), usage));
        when(costCalculator.computeCost("mock-v1", usage)).thenReturn(new BigDecimal("0.000280"));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        assertThat(captor.getValue().getCostDecimal()).isEqualByComparingTo("0.000280");
        assertThat(captor.getValue().getCostDecimal()).isNotEqualByComparingTo(providerResult().cost());
        verify(costCalculator).computeCost("mock-v1", usage);
    }

    @Test
    void failed_provider_attempt_persists_failed_ai_calls_row_with_attempt_suffix_key() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenThrow(retryableProviderException("timeout"));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiProviderFailureException.class)
            .hasCauseInstanceOf(AiProviderException.class);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(AiCallEntity::getStatus)
            .containsExactly(AiCallStatusCodes.FAILED, AiCallStatusCodes.FAILED, AiCallStatusCodes.FAILED);
        assertThat(captor.getAllValues()).extracting(AiCallEntity::getIdempotencyKey)
            .containsExactly(
                canonicalKey() + "#failed-attempt-1",
                canonicalKey() + "#failed-attempt-2",
                canonicalKey() + "#failed-attempt-3"
            );
        verify(aiProvider, times(3)).invokeWithUsage(any(AiCallRequest.class));
        verify(aiCallInFieldMapper, never()).insert(any());
    }

    @Test
    void successful_call_uses_canonical_idempotency_key() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenReturn(new ProviderInvocationResult(providerResult(), null));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        AiCallEntity row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo(AiCallStatusCodes.COMPLETED);
        assertThat(row.getIdempotencyKey()).isEqualTo(canonicalKey());
    }

    @Test
    void eventual_success_after_failure_writes_both_failed_and_success_rows() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenThrow(retryableProviderException("timeout"))
            .thenReturn(new ProviderInvocationResult(providerResult(), null));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(AiCallEntity::getStatus)
            .containsExactly(AiCallStatusCodes.FAILED, AiCallStatusCodes.COMPLETED);
        assertThat(captor.getAllValues()).extracting(AiCallEntity::getIdempotencyKey)
            .containsExactly(canonicalKey() + "#failed-attempt-1", canonicalKey());
        verify(aiCallMapper, times(1)).selectByIdempotencyKey(canonicalKey());
        verify(metrics).recordMiss("mock");
        verify(metrics).recordRetryAttempt("mock");
        verify(metrics, never()).recordHit(any());
    }

    @Test
    void retryable_exception_triggers_retry_up_to_max_attempts() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenThrow(retryableProviderException("rate_limit"));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiProviderFailureException.class);

        verify(aiProvider, times(3)).invokeWithUsage(any(AiCallRequest.class));
    }

    @Test
    void non_retryable_exception_does_not_retry() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenThrow(nonRetryableProviderException());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiProviderFailureException.class);

        verify(aiProvider, times(1)).invokeWithUsage(any(AiCallRequest.class));
        ArgumentCaptor<AiCallEntity> captor = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(canonicalKey() + "#failed-attempt-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(AiCallStatusCodes.FAILED);
        verify(metrics, never()).recordRetryAttempt(any());
    }

    @Test
    void miss_counter_increments_once_per_logical_review_regardless_of_retries() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenThrow(retryableProviderException("timeout"))
            .thenThrow(retryableProviderException("timeout"))
            .thenReturn(new ProviderInvocationResult(providerResult(), null));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        verify(metrics, times(1)).recordMiss("mock");
        verify(metrics, never()).recordHit(any());
        verify(metrics, never()).recordMismatch(any());
    }

    @Test
    void retry_attempts_increment_retry_counter_separately() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenThrow(retryableProviderException("timeout"))
            .thenReturn(new ProviderInvocationResult(providerResult(), null));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        verify(metrics).recordRetryAttempt("mock");
        verify(metrics).recordMiss("mock");
        verify(metrics, never()).recordHit(any());
    }

    @Test
    void review_does_not_persist_field_findings_when_provider_returns_empty() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(emptyProviderResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);

        AiReviewResultView result = service.review(300L, 1001L, 1L);

        assertThat(result.fieldRows()).isEmpty();
        verify(aiCallInFieldMapper, never()).insert(any());
        verify(ledgerService, never()).appendAiFieldFindings(any(), any(), any(), any());
    }

    @Test
    void ai_field_findings_appended_to_ledger_on_new_review() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(twoFindingProviderResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);
        when(ledgerService.appendAiFieldFindings(any(), any(), any(), any()))
            .thenReturn(List.of(ledgerEntry(1000L), ledgerEntry(1001L)));

        AiReviewResultView result = service.review(300L, 1001L, 1L);

        assertThat(result.idempotencyHit()).isFalse();
        verify(ledgerService).appendAiFieldFindings(
            eq(300L),
            eq(10L),
            eq(900L),
            argThat(findings -> findings.size() == 2)
        );
        AuditEvent event = capturedRecordEvent();
        assertThat(event.action()).isEqualTo(AuditActions.AI_REVIEW_FIELD_ASSIST);
        assertThat(event.actorType()).isEqualTo("ai");
        assertThat(event.resourceType()).isEqualTo("submission");
    }

    @Test
    void failed_review_writesRequiresNewAuditEvent() {
        when(aiProvider.invokeWithUsage(any(AiCallRequest.class)))
            .thenThrow(nonRetryableProviderException());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiProviderFailureException.class);

        AuditEvent event = capturedRequiresNewEvent();
        assertThat(event.action()).isEqualTo(AuditActions.AI_REVIEW_FAILED);
        assertThat(event.actorType()).isEqualTo("system");
        assertThat(event.resourceType()).isEqualTo("submission");
    }

    @Test
    void ai_review_does_not_mutate_submission_status() {
        SubmissionEntity submission = submission();
        submission.setStatusCode("submitted");
        when(submissionMapper.selectById(300L)).thenReturn(submission);
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        assertThat(submission.getStatusCode()).isEqualTo("submitted");
    }

    @Test
    void ai_pass_appends_ai_overall_recommendation_but_not_reviewer_verdict() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ledgerService).appendAiOverallRecommendation(eq(300L), eq(10L), eq(900L), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).containsEntry("recommendation", "pass");
        assertThat(payloadCaptor.getValue()).containsKey("scoringRuleVersion");
        verify(ledgerService, never()).createEntry(any(), any(), eq("reviewer_overall_verdict"), any());
    }

    @Test
    void review_returns_existing_result_when_idempotency_key_matches_and_input_hash_matches() {
        String inputHash = inputHashForDefaultFixture();
        AiCallEntity existing = persistedAiCall(inputHash);
        when(aiCallMapper.selectByIdempotencyKey(existing.getIdempotencyKey())).thenReturn(existing);
        when(aiCallInFieldMapper.selectBySubmissionAndAiCall(300L, 900L)).thenReturn(List.of(fieldRow(300L, 900L, "field-title", 1)));

        AiReviewResultView result = service.review(300L, 1001L, 1L);

        assertThat(result.idempotencyHit()).isTrue();
        assertThat(result.providerResult().overallSuggestion()).isEqualTo("pass");
        assertThat(result.providerResult().fieldFindings()).extracting(FieldFinding::fieldPath).containsExactly("field-title");
        verify(aiProvider, never()).invoke(any());
        verify(aiCallMapper, never()).insert(any());
        verify(aiCallInFieldMapper, never()).insert(any());
        verify(ledgerService, never()).appendAiFieldFindings(any(), any(), any(), any());
    }

    @Test
    void review_increments_hit_counter_when_idempotency_key_matches() {
        String inputHash = inputHashForDefaultFixture();
        AiCallEntity existing = persistedAiCall(inputHash);
        when(aiCallMapper.selectByIdempotencyKey(existing.getIdempotencyKey())).thenReturn(existing);
        when(aiCallInFieldMapper.selectBySubmissionAndAiCall(300L, 900L)).thenReturn(List.of(fieldRow(300L, 900L, "field-title", 1)));

        service.review(300L, 1001L, 1L);

        verify(metrics).recordHit("mock");
        verify(metrics, never()).recordMiss(any());
        verify(metrics, never()).recordMismatch(any());
    }

    @Test
    void review_increments_miss_counter_on_new_invocation() {
        when(aiProvider.invoke(any(AiCallRequest.class))).thenReturn(providerResult());
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenReturn(1);
        when(aiCallInFieldMapper.insert(any())).thenReturn(1);

        service.review(300L, 1001L, 1L);

        verify(metrics).recordMiss("mock");
        verify(metrics, never()).recordHit(any());
        verify(metrics, never()).recordMismatch(any());
    }

    @Test
    void review_throws_ai_input_hash_mismatch_when_key_matches_but_hash_differs() {
        AiCallEntity existing = persistedAiCall("different-input-hash");
        when(aiCallMapper.selectByIdempotencyKey(existing.getIdempotencyKey())).thenReturn(existing);

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiInputHashMismatchException.class);

        verify(aiProvider, never()).invoke(any());
    }

    @Test
    void review_increments_mismatch_counter_on_input_hash_difference() {
        AiCallEntity existing = persistedAiCall("different-input-hash");
        when(aiCallMapper.selectByIdempotencyKey(existing.getIdempotencyKey())).thenReturn(existing);

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiInputHashMismatchException.class);

        verify(metrics).recordMismatch("mock");
        verify(metrics, never()).recordHit(any());
        verify(metrics, never()).recordMiss(any());
    }

    @Test
    void getProvenance_sets_output_hash_for_each_ai_call() {
        AiCallEntity aiCall = persistedAiCall(inputHashForDefaultFixture());
        when(aiCallMapper.selectBySubmissionId(300L)).thenReturn(List.of(aiCall));
        when(aiCallInFieldMapper.selectBySubmissionId(300L)).thenReturn(List.of(fieldRow(300L, 900L, "field-title", 1)));

        SubmissionAiProvenanceView result = service.getProvenance(300L, 1001L);

        assertThat(result.aiCalls()).singleElement()
            .extracting(AiCallEntity::getOutputHash)
            .isEqualTo(canonicalizer.sha256Hex(canonicalizer.canonicalJson(jsonRoundTrip(providerResult().output()))));
        assertThat(result.fieldRows()).hasSize(1);
    }

    @Test
    void getProvenance_allows_reviewer_to_read_any_submission() {
        TaskEntity task = task(10L, 2002L);
        when(taskMapper.selectById(10L)).thenReturn(task);
        AiCallEntity aiCall = persistedAiCall(inputHashForDefaultFixture());
        when(aiCallMapper.selectBySubmissionId(300L)).thenReturn(List.of(aiCall));

        SubmissionAiProvenanceView result = service.getProvenance(300L, 3003L, Set.of("REVIEWER"));

        assertThat(result.aiCalls()).hasSize(1);
    }

    @Test
    void review_throws_submission_not_found_when_submission_missing() {
        when(submissionMapper.selectById(300L)).thenReturn(null);

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(SubmissionNotFoundException.class);

        verify(aiProvider, never()).invoke(any());
    }

    @Test
    void review_throws_prompt_version_not_found_when_prompt_version_id_is_unknown() {
        when(promptVersionService.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.review(300L, 1001L, 99L))
            .isInstanceOf(PromptVersionNotFoundException.class)
            .hasMessageContaining("99");

        verify(aiProvider, never()).invoke(any());
        verify(aiCallMapper, never()).insert(any());
    }

    @Test
    void review_wraps_provider_exception_as_ai_provider_failure_after_failed_attempt_evidence() {
        when(aiProvider.invokeWithUsage(any())).thenThrow(new AiProviderException("rate limited", true, "rate_limit", 429));
        when(aiCallMapper.insert(any(AiCallEntity.class))).thenAnswer(assignAiCallIds());

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(AiProviderFailureException.class)
            .hasCauseInstanceOf(AiProviderException.class);

        verify(aiCallMapper, times(3)).insert(any(AiCallEntity.class));
        verify(aiCallInFieldMapper, never()).insert(any());
    }

    @Test
    void review_throws_submission_not_found_when_task_belongs_to_different_owner() {
        TaskEntity task = task(10L, 2002L);
        when(taskMapper.selectById(10L)).thenReturn(task);

        assertThatThrownBy(() -> service.review(300L, 1001L, 1L))
            .isInstanceOf(SubmissionNotFoundException.class);

        verify(aiProvider, never()).invoke(any());
    }

    private void seedOwnedSubmission() {
        when(submissionMapper.selectById(300L)).thenReturn(submission());
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(schemaVersionMapper.selectById(700L)).thenReturn(schemaVersion());
        when(datasetItemMapper.selectById(500L)).thenReturn(datasetItem());
    }

    private Answer<Integer> assignAiCallIds() {
        AtomicLong nextId = new AtomicLong(900L);
        return invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(nextId.getAndIncrement());
            return 1;
        };
    }

    private String canonicalKey() {
        return "submission:300:provider:mock:model:mock-v1:promptVersionId:1:adapter:agent-default-v1";
    }

    private PromptVersionEntity defaultPromptVersion() {
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setId(1L);
        entity.setVersionNumber(1);
        entity.setContent("m3-owner-review-v1");
        entity.setContentHash("fa76977fd0bdc3f0cc7336855006669f2950381f1a0dc4f0803458bb6f06d456");
        entity.setStatusCode("published");
        return entity;
    }

    private PromptVersionEntity rulePromptVersion() {
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setId(7L);
        entity.setVersionNumber(2);
        entity.setContent("task specific prompt");
        entity.setContentHash("b".repeat(64));
        entity.setStatusCode("published");
        return entity;
    }

    private AiReviewRuleEntity activeRule(Long id, Long taskId, Long promptVersionId) {
        AiReviewRuleEntity entity = new AiReviewRuleEntity();
        entity.setId(id);
        entity.setTaskId(taskId);
        entity.setVersionNumber(3);
        entity.setCurrentPromptVersionId(promptVersionId);
        entity.setDimensionsJson("[\"quality\"]");
        entity.setThreshold(new BigDecimal("0.80"));
        entity.setStatusCode("published");
        entity.setCreatedBy(1001L);
        return entity;
    }

    private AiProviderException retryableProviderException(String providerCode) {
        return new AiProviderException("temporary " + providerCode, true, providerCode, 503);
    }

    private AiProviderException nonRetryableProviderException() {
        return new AiProviderException("bad request", false, "bad_request", 400);
    }

    private String inputHashForDefaultFixture() {
        return canonicalizer.sha256Hex(canonicalizer.canonicalJson(expectedInput()));
    }

    private Map<String, Object> expectedInput() {
        return Map.of(
            "schemaVersionId", 700L,
            "schemaFields", List.of(Map.of(
                "stableId", "field-title",
                "label", "标题",
                "type", "text"
            )),
            "answerPayload", Map.of("field-title", "good answer"),
            "datasetItemPayload", Map.of("source", "row-1"),
            "task", Map.of("id", 10L, "title", "Task title", "description", "Task description"),
            "submission", Map.of("id", 300L, "createdAt", "2026-05-25T11:00")
        );
    }

    private AiCallResult providerResult() {
        FieldFinding finding = new FieldFinding("field-title", "field-title", "标题", "info", "looks fine", new BigDecimal("0.90"));
        Map<String, Object> output = output(List.of(finding), "summary");
        return new AiCallResult(output, "pass", new BigDecimal("0.90"), "summary",
            List.of(finding), 10, 20, new BigDecimal("0.000100"), 100, null);
    }

    private AiCallResult twoFindingProviderResult() {
        FieldFinding first = new FieldFinding("field-title", "field-title", "标题", "warning", "tighten title", new BigDecimal("0.80"));
        FieldFinding second = new FieldFinding("field-body", "field-body", "正文", "info", "body looks fine", new BigDecimal("0.70"));
        List<FieldFinding> findings = List.of(first, second);
        Map<String, Object> output = output(findings, "two findings");
        return new AiCallResult(output, "manual_review", new BigDecimal("0.75"), "two findings",
            findings, 10, 20, new BigDecimal("0.000100"), 100, null);
    }

    private AiCallResult emptyProviderResult() {
        Map<String, Object> output = output(List.of(), "empty");
        return new AiCallResult(output, "pass", new BigDecimal("0.90"), "empty",
            List.of(), 10, 0, new BigDecimal("0.000100"), 100, null);
    }

    private Map<String, Object> output(List<FieldFinding> findings, String summary) {
        return Map.of(
            "overallSuggestion", "pass",
            "confidence", new BigDecimal("0.90"),
            "summary", summary,
            "dimensionScores", List.of(Map.of(
                "dimension", "quality",
                "score", new BigDecimal("0.90"),
                "reason", "fixture score"
            )),
            "fieldFindings", findings.stream().map(finding -> Map.of(
                "fieldPath", finding.fieldPath(),
                "stableId", finding.stableId(),
                "label", finding.label(),
                "severity", finding.severity(),
                "finding", finding.finding(),
                "confidence", finding.confidence()
            )).toList()
        );
    }

    private Map<String, Object> jsonRoundTrip(Map<String, Object> value) {
        try {
            return objectMapper.readValue(
                objectMapper.writeValueAsString(value),
                new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to round-trip JSON fixture", exception);
        }
    }

    private AiCallEntity persistedAiCall(String inputHash) {
        AiCallEntity entity = new AiCallEntity();
        entity.setId(900L);
        entity.setSubmissionId(300L);
        entity.setPurpose("submission_review");
        entity.setPromptVersion("promptVersion#1");
        entity.setPromptVersionId(1L);
        entity.setProviderAdapterVersion("agent-default-v1");
        entity.setModelProvider("mock");
        entity.setModelName("mock-v1");
        entity.setInputHash(inputHash);
        entity.setResponsePayload(jsonRoundTrip(providerResult().output()));
        entity.setTokenInput(10);
        entity.setTokenOutput(20);
        entity.setCostDecimal(new BigDecimal("0.000100"));
        entity.setLatencyMs(100);
        entity.setStatus(AiCallStatusCodes.COMPLETED);
        entity.setIdempotencyKey(canonicalKey());
        entity.setCreatedAt(NOW);
        entity.setCompletedAt(NOW);
        return entity;
    }

    private AiCallInFieldEntity fieldRow(Long submissionId, Long aiCallId, String fieldPath, int ordinal) {
        AiCallInFieldEntity entity = new AiCallInFieldEntity();
        entity.setSubmissionId(submissionId);
        entity.setAiCallId(aiCallId);
        entity.setFieldPath(fieldPath);
        entity.setAccepted(false);
        entity.setUserModifiedAfter(false);
        entity.setOrdinal(ordinal);
        entity.setCreatedAt(NOW);
        return entity;
    }

    private QualityLedgerEntryEntity ledgerEntry(Long id) {
        QualityLedgerEntryEntity entity = new QualityLedgerEntryEntity();
        entity.setId(id);
        return entity;
    }

    private AuditEvent capturedRecordEvent() {
        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(captor.capture());
        return captor.getValue().build();
    }

    private AuditEvent capturedRequiresNewEvent() {
        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).recordRequiresNew(captor.capture());
        return captor.getValue().build();
    }

    private SubmissionEntity submission() {
        SubmissionEntity entity = new SubmissionEntity();
        entity.setId(300L);
        entity.setTaskId(10L);
        entity.setDatasetItemId(500L);
        entity.setSchemaVersionId(700L);
        entity.setLabelerId(1002L);
        entity.setAnswerPayload(Map.of("field-title", "good answer"));
        entity.setCreatedAt(LocalDateTime.parse("2026-05-25T11:00:00"));
        return entity;
    }

    private SchemaVersionEntity schemaVersion() {
        SchemaVersionEntity entity = new SchemaVersionEntity();
        entity.setId(700L);
        entity.setSchemaJson(Map.of("fields", List.of(Map.of(
            "stableId", "field-title",
            "label", "标题",
            "type", "text"
        ))));
        return entity;
    }

    private SchemaVersionEntity jsonSchemaVersion() {
        SchemaVersionEntity entity = new SchemaVersionEntity();
        entity.setId(700L);
        entity.setSchemaJson(Map.of(
            "x-labelhub-schemaFormatVersion", 2,
            "$schema", "https://json-schema.org/draft/2020-12/schema",
            "type", "object",
            "properties", Map.of("field-title", Map.of("type", "string")),
            "x-labelhub-fields", List.of(Map.of(
                "stableId", "field-title",
                "label", "标题",
                "type", "text"
            ))
        ));
        return entity;
    }

    private DatasetItemEntity datasetItem() {
        DatasetItemEntity entity = new DatasetItemEntity();
        entity.setId(500L);
        entity.setItemPayload(Map.of("source", "row-1"));
        return entity;
    }

    private TaskEntity task(Long id, Long ownerId) {
        TaskEntity entity = new TaskEntity();
        entity.setId(id);
        entity.setOwnerId(ownerId);
        entity.setTitle("Task title");
        entity.setDescription("Task description");
        return entity;
    }
}
