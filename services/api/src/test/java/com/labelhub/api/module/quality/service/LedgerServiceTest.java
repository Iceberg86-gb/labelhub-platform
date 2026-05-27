package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEvent;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.ai.provider.FieldFinding;
import com.labelhub.api.module.quality.exception.LedgerEntryPayloadInvalidException;
import com.labelhub.api.module.quality.exception.LedgerEntryTypeNotSupportedException;
import com.labelhub.api.module.quality.exception.SelfReviewNotAllowedException;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.PagedResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerServiceTest {

    private static final Long SUBMISSION_ID = 300L;
    private static final Long TASK_ID = 20L;
    private static final Long OWNER_ID = 1001L;
    private static final Long LABELER_ID = 2002L;
    private static final Long REVIEWER_ID = 3003L;
    private static final Long STRANGER_ID = 4004L;
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-25T09:30:00");

    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper = mock(QualityLedgerEntryMapper.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-25T09:30:00Z"), ZoneOffset.UTC);
        ledgerService = new LedgerService(submissionMapper, taskMapper, qualityLedgerEntryMapper, clock, auditLogService);
    }

    @Test
    void createEntry_writes_append_only_ledger_entry() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        doAnswer(invocation -> {
            QualityLedgerEntryEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        }).when(qualityLedgerEntryMapper).insert(any(QualityLedgerEntryEntity.class));

        QualityLedgerEntryEntity entry = ledgerService.createEntry(
            SUBMISSION_ID,
            REVIEWER_ID,
            "reviewer_overall_verdict",
            Map.of("verdict", "approve", "reason", "Looks consistent")
        );

        assertThat(entry.getId()).isEqualTo(900L);
        assertThat(entry.getSubmissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(entry.getTaskId()).isEqualTo(TASK_ID);
        assertThat(entry.getEvidenceType()).isEqualTo("reviewer_overall_verdict");
        assertThat(entry.getActorType()).isEqualTo("reviewer");
        assertThat(entry.getActorId()).isEqualTo(REVIEWER_ID);
        assertThat(entry.getAiCallId()).isNull();
        assertThat(entry.getPayload()).containsEntry("verdict", "approve");
        assertThat(entry.getCreatedAt()).isEqualTo(NOW);
        verify(qualityLedgerEntryMapper).insert(any(QualityLedgerEntryEntity.class));
        assertAuditEvent(AuditActions.REVIEW_APPROVE, "user", "submission");
    }

    @Test
    void createEntry_sets_task_id_from_submission_to_align_indexing() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        doAnswer(invocation -> 1).when(qualityLedgerEntryMapper).insert(any(QualityLedgerEntryEntity.class));

        ledgerService.createEntry(SUBMISSION_ID, REVIEWER_ID, "reviewer_overall_verdict", Map.of("verdict", "reject"));

        ArgumentCaptor<QualityLedgerEntryEntity> captor = ArgumentCaptor.forClass(QualityLedgerEntryEntity.class);
        verify(qualityLedgerEntryMapper).insert(captor.capture());
        assertThat(captor.getValue().getTaskId()).isEqualTo(TASK_ID);
        assertAuditEvent(AuditActions.REVIEW_REJECT, "user", "submission");
    }

    @Test
    void createEntry_rejects_unsupported_entry_type() {
        assertThatThrownBy(() -> ledgerService.createEntry(
            SUBMISSION_ID,
            REVIEWER_ID,
            "ai_field_finding",
            Map.of("verdict", "approve")
        )).isInstanceOf(LedgerEntryTypeNotSupportedException.class);

        verify(submissionMapper, never()).selectById(any());
        verify(qualityLedgerEntryMapper, never()).insert(any());
    }

    @Test
    void createEntry_rejects_self_review() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());

        assertThatThrownBy(() -> ledgerService.createEntry(
            SUBMISSION_ID,
            LABELER_ID,
            "reviewer_overall_verdict",
            Map.of("verdict", "approve")
        )).isInstanceOf(SelfReviewNotAllowedException.class);

        verify(qualityLedgerEntryMapper, never()).insert(any());
    }

    @Test
    void createEntry_rejects_when_submission_not_found() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(null);

        assertThatThrownBy(() -> ledgerService.createEntry(
            SUBMISSION_ID,
            REVIEWER_ID,
            "reviewer_overall_verdict",
            Map.of("verdict", "approve")
        )).isInstanceOf(SubmissionNotFoundException.class);

        verify(qualityLedgerEntryMapper, never()).insert(any());
    }

    @Test
    void createEntry_rejects_payload_without_verdict_field() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());

        assertThatThrownBy(() -> ledgerService.createEntry(
            SUBMISSION_ID,
            REVIEWER_ID,
            "reviewer_overall_verdict",
            Map.of("reason", "Missing verdict")
        )).isInstanceOf(LedgerEntryPayloadInvalidException.class);

        verify(qualityLedgerEntryMapper, never()).insert(any());
    }

    @Test
    void createEntry_rejects_payload_with_invalid_verdict_value() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());

        assertThatThrownBy(() -> ledgerService.createEntry(
            SUBMISSION_ID,
            REVIEWER_ID,
            "reviewer_overall_verdict",
            Map.of("verdict", "maybe")
        )).isInstanceOf(LedgerEntryPayloadInvalidException.class);

        verify(qualityLedgerEntryMapper, never()).insert(any());
    }

    @Test
    void listEntries_returns_paged_entries_for_labeler_of_submission() {
        QualityLedgerEntryEntity entry = entry(901L, "approve");
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(qualityLedgerEntryMapper.selectBySubmissionId(SUBMISSION_ID, 20L, 10L)).thenReturn(List.of(entry));
        when(qualityLedgerEntryMapper.selectCountBySubmissionId(SUBMISSION_ID)).thenReturn(1L);

        PagedResult<QualityLedgerEntryEntity> result =
            ledgerService.listEntries(SUBMISSION_ID, LABELER_ID, Set.of(), 3, 10);

        assertThat(result.items()).extracting(QualityLedgerEntryEntity::getId).containsExactly(901L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.page()).isEqualTo(3L);
        assertThat(result.size()).isEqualTo(10L);
    }

    @Test
    void listEntries_returns_paged_entries_for_task_owner() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(qualityLedgerEntryMapper.selectBySubmissionId(SUBMISSION_ID, 0L, 20L)).thenReturn(List.of(entry(902L, "reject")));
        when(qualityLedgerEntryMapper.selectCountBySubmissionId(SUBMISSION_ID)).thenReturn(1L);

        PagedResult<QualityLedgerEntryEntity> result =
            ledgerService.listEntries(SUBMISSION_ID, OWNER_ID, Set.of(), 1, 20);

        assertThat(result.items()).extracting(QualityLedgerEntryEntity::getId).containsExactly(902L);
    }

    @Test
    void listEntries_returns_paged_entries_for_reviewer_role() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(qualityLedgerEntryMapper.selectBySubmissionId(SUBMISSION_ID, 0L, 20L)).thenReturn(List.of(entry(903L, "approve")));
        when(qualityLedgerEntryMapper.selectCountBySubmissionId(SUBMISSION_ID)).thenReturn(1L);

        PagedResult<QualityLedgerEntryEntity> result =
            ledgerService.listEntries(SUBMISSION_ID, REVIEWER_ID, Set.of("ROLE_REVIEWER"), 1, 20);

        assertThat(result.items()).extracting(QualityLedgerEntryEntity::getId).containsExactly(903L);
        verify(taskMapper, never()).selectById(any());
    }

    @Test
    void listEntries_throws_when_requester_is_neither_owner_labeler_nor_reviewer() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());

        assertThatThrownBy(() -> ledgerService.listEntries(SUBMISSION_ID, STRANGER_ID, Set.of("LABELER"), 1, 20))
            .isInstanceOf(SubmissionNotFoundException.class);

        verify(qualityLedgerEntryMapper, never()).selectBySubmissionId(any(), any(), any());
    }

    @Test
    void appendAiFieldFindings_writes_one_ledger_entry_per_finding() {
        doAnswer(invocation -> {
            QualityLedgerEntryEntity entity = invocation.getArgument(0);
            entity.setId(entity.getId() == null ? 1000L : entity.getId());
            return 1;
        }).when(qualityLedgerEntryMapper).insert(any(QualityLedgerEntryEntity.class));
        List<FieldFinding> findings = List.of(
            new FieldFinding("field_a", null, null, "warning", "fix A", new BigDecimal("0.90")),
            new FieldFinding("field_b", null, null, "info", "ok B", null),
            new FieldFinding("field_c", "stable_c", "Label C", "error", "fix C", new BigDecimal("0.70"))
        );

        List<QualityLedgerEntryEntity> result =
            ledgerService.appendAiFieldFindings(SUBMISSION_ID, TASK_ID, 800L, findings);

        assertThat(result).hasSize(3);
        QualityLedgerEntryEntity entry = result.get(0);
        assertThat(entry.getEvidenceType()).isEqualTo("ai_field_finding");
        assertThat(entry.getActorType()).isEqualTo("ai");
        assertThat(entry.getActorId()).isNull();
        assertThat(entry.getAiCallId()).isEqualTo(800L);
        assertThat(entry.getCreatedAt()).isEqualTo(NOW);
        assertThat(entry.getPayload())
            .containsEntry("fieldPath", "field_a")
            .containsEntry("severity", "warning")
            .containsEntry("finding", "fix A")
            .doesNotContainKey("aiCallId");
        verify(qualityLedgerEntryMapper, times(3)).insert(any(QualityLedgerEntryEntity.class));
    }

    @Test
    void appendAiFieldFindings_with_empty_list_writes_nothing() {
        List<QualityLedgerEntryEntity> result =
            ledgerService.appendAiFieldFindings(SUBMISSION_ID, TASK_ID, 800L, List.of());

        assertThat(result).isEmpty();
        verify(qualityLedgerEntryMapper, never()).insert(any());
    }

    @Test
    void appendAiFieldFindings_optional_fields_omitted_when_null() {
        when(qualityLedgerEntryMapper.insert(any(QualityLedgerEntryEntity.class))).thenReturn(1);
        FieldFinding finding = new FieldFinding("field_x", null, null, "info", "ok", null);

        List<QualityLedgerEntryEntity> result =
            ledgerService.appendAiFieldFindings(SUBMISSION_ID, TASK_ID, 800L, List.of(finding));

        Map<String, Object> payload = result.get(0).getPayload();
        assertThat(payload).containsKeys("fieldPath", "severity", "finding");
        assertThat(payload).doesNotContainKeys("stableId", "label", "confidence", "aiCallId");
    }

    private static SubmissionEntity submission() {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(SUBMISSION_ID);
        submission.setTaskId(TASK_ID);
        submission.setLabelerId(LABELER_ID);
        submission.setSchemaVersionId(80L);
        submission.setStatusCode("submitted");
        return submission;
    }

    private static TaskEntity task() {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        task.setOwnerId(OWNER_ID);
        return task;
    }

    private static QualityLedgerEntryEntity entry(Long id, String verdict) {
        QualityLedgerEntryEntity entry = new QualityLedgerEntryEntity();
        entry.setId(id);
        entry.setSubmissionId(SUBMISSION_ID);
        entry.setTaskId(TASK_ID);
        entry.setEvidenceType("reviewer_overall_verdict");
        entry.setActorType("reviewer");
        entry.setActorId(REVIEWER_ID);
        entry.setPayload(Map.of("verdict", verdict));
        entry.setCreatedAt(NOW);
        return entry;
    }

    private void assertAuditEvent(String action, String actorType, String resourceType) {
        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(captor.capture());
        AuditEvent event = captor.getValue().build();
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.actorType()).isEqualTo(actorType);
        assertThat(event.resourceType()).isEqualTo(resourceType);
    }
}
