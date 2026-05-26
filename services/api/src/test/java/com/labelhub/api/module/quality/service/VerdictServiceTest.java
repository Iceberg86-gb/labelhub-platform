package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.service.view.VerdictView;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerdictServiceTest {

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
    private VerdictService verdictService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-25T09:30:00Z"), ZoneOffset.UTC);
        verdictService = new VerdictService(submissionMapper, taskMapper, qualityLedgerEntryMapper, clock);
    }

    @Test
    void deriveCurrentVerdict_returns_pending_when_no_ledger_entry() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(SUBMISSION_ID)).thenReturn(null);

        VerdictView verdict = verdictService.deriveCurrentVerdict(SUBMISSION_ID, REVIEWER_ID, Set.of("REVIEWER"));

        assertThat(verdict.submissionId()).isEqualTo(SUBMISSION_ID);
        assertThat(verdict.status()).isEqualTo("pending");
        assertThat(verdict.derivedFromEntryId()).isNull();
        assertThat(verdict.derivedAt()).isEqualTo(NOW);
    }

    @Test
    void deriveCurrentVerdict_derives_approved_from_latest_approve_entry() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(SUBMISSION_ID))
            .thenReturn(entry(101L, "approve"));

        VerdictView verdict = verdictService.deriveCurrentVerdict(SUBMISSION_ID, OWNER_ID, Set.of());

        assertThat(verdict.status()).isEqualTo("approved");
        assertThat(verdict.derivedFromEntryId()).isEqualTo(101L);
    }

    @Test
    void deriveCurrentVerdict_derives_rejected_from_latest_reject_entry() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(SUBMISSION_ID))
            .thenReturn(entry(102L, "reject"));

        VerdictView verdict = verdictService.deriveCurrentVerdict(SUBMISSION_ID, REVIEWER_ID, Set.of("ROLE_REVIEWER"));

        assertThat(verdict.status()).isEqualTo("rejected");
        assertThat(verdict.derivedFromEntryId()).isEqualTo(102L);
    }

    @Test
    void new_ledger_entry_changes_verdict() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());
        when(qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(SUBMISSION_ID))
            .thenReturn(null)
            .thenReturn(entry(101L, "approve"))
            .thenReturn(entry(102L, "reject"));

        VerdictView v0 = verdictService.deriveCurrentVerdict(SUBMISSION_ID, OWNER_ID, Set.of());
        assertThat(v0.status()).isEqualTo("pending");
        assertThat(v0.derivedFromEntryId()).isNull();

        VerdictView v1 = verdictService.deriveCurrentVerdict(SUBMISSION_ID, OWNER_ID, Set.of());
        assertThat(v1.status()).isEqualTo("approved");
        assertThat(v1.derivedFromEntryId()).isEqualTo(101L);

        VerdictView v2 = verdictService.deriveCurrentVerdict(SUBMISSION_ID, OWNER_ID, Set.of());
        assertThat(v2.status()).isEqualTo("rejected");
        assertThat(v2.derivedFromEntryId()).isEqualTo(102L);
    }

    @Test
    void verdict_tie_breaks_by_id_when_created_at_equal() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        QualityLedgerEntryEntity higherIdEntry = entry(202L, "reject");
        higherIdEntry.setCreatedAt(NOW);
        when(qualityLedgerEntryMapper.selectLatestReviewerOverallVerdict(SUBMISSION_ID)).thenReturn(higherIdEntry);

        VerdictView verdict = verdictService.deriveCurrentVerdict(SUBMISSION_ID, REVIEWER_ID, Set.of("REVIEWER"));

        assertThat(verdict.status()).isEqualTo("rejected");
        assertThat(verdict.derivedFromEntryId()).isEqualTo(202L);
    }

    @Test
    void deriveCurrentVerdict_throws_when_requester_neither_owner_labeler_nor_reviewer() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(taskMapper.selectById(TASK_ID)).thenReturn(task());

        assertThatThrownBy(() -> verdictService.deriveCurrentVerdict(SUBMISSION_ID, STRANGER_ID, Set.of("LABELER")))
            .isInstanceOf(SubmissionNotFoundException.class);

        verify(qualityLedgerEntryMapper, never()).selectLatestReviewerOverallVerdict(any());
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
}
