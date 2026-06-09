package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.entity.SeniorReviewCaseEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.mapper.SeniorReviewCaseMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeniorReviewCaseServiceTest {

    private static final Long SUBMISSION_ID = 300L;
    private static final Long TASK_ID = 20L;
    private static final Long LABELER_ID = 2002L;
    private static final Long REVIEWER_ID = 3003L;
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-25T09:30:00");

    private final SeniorReviewCaseMapper seniorReviewCaseMapper = mock(SeniorReviewCaseMapper.class);
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper = mock(QualityLedgerEntryMapper.class);
    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private SeniorReviewCaseService seniorReviewCaseService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-25T09:30:00Z"), ZoneOffset.UTC);
        seniorReviewCaseService = new SeniorReviewCaseService(
            seniorReviewCaseMapper,
            qualityLedgerEntryMapper,
            submissionMapper,
            null,
            null,
            clock
        );
    }

    @Test
    void recordAiOverallRecommendation_opens_pending_case_for_manual_review_signal() {
        QualityLedgerEntryEntity aiOverall = aiOverallEntry(800L, "manual_review");
        when(seniorReviewCaseMapper.selectByCaseKey("submission:300:ai_manual_review")).thenReturn(null);
        doAnswer(invocation -> {
            SeniorReviewCaseEntity entity = invocation.getArgument(0);
            entity.setId(700L);
            return 1;
        }).when(seniorReviewCaseMapper).insert(any(SeniorReviewCaseEntity.class));

        SeniorReviewCaseEntity created =
            seniorReviewCaseService.recordAiOverallRecommendation(SUBMISSION_ID, TASK_ID, aiOverall);

        assertThat(created.getId()).isEqualTo(700L);
        assertThat(created.getCaseType()).isEqualTo("arbitration");
        assertThat(created.getSourceSignal()).isEqualTo("ai_manual_review");
        assertThat(created.getStatus()).isEqualTo("pending_reviewer");
        assertThat(created.getAiOverallEntryId()).isEqualTo(800L);
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void activateCasesAfterReviewerApprove_opens_existing_pending_manual_review_case() {
        SubmissionEntity submission = submission();
        QualityLedgerEntryEntity reviewerEntry = reviewerEntry(900L, "approve");
        when(seniorReviewCaseMapper.openPendingCasesForReviewerApprove(SUBMISSION_ID, 900L, REVIEWER_ID, NOW))
            .thenReturn(1);
        when(qualityLedgerEntryMapper.selectHighConfidenceErrorFieldFindings(SUBMISSION_ID, new BigDecimal("0.85")))
            .thenReturn(List.of());

        seniorReviewCaseService.activateCasesAfterReviewerApprove(submission, reviewerEntry);

        verify(seniorReviewCaseMapper)
            .openPendingCasesForReviewerApprove(SUBMISSION_ID, 900L, REVIEWER_ID, NOW);
    }

    @Test
    void activateCasesAfterReviewerApprove_opens_conflict_case_for_high_confidence_ai_error() {
        SubmissionEntity submission = submission();
        QualityLedgerEntryEntity reviewerEntry = reviewerEntry(900L, "approve");
        QualityLedgerEntryEntity finding = fieldFindingEntry(801L, "error", "0.91");
        when(qualityLedgerEntryMapper.selectHighConfidenceErrorFieldFindings(SUBMISSION_ID, new BigDecimal("0.85")))
            .thenReturn(List.of(finding));
        when(seniorReviewCaseMapper.selectByCaseKey("submission:300:ai_error_conflict")).thenReturn(null);
        doAnswer(invocation -> {
            SeniorReviewCaseEntity entity = invocation.getArgument(0);
            entity.setId(701L);
            return 1;
        }).when(seniorReviewCaseMapper).insert(any(SeniorReviewCaseEntity.class));

        SeniorReviewCaseEntity created =
            seniorReviewCaseService.activateCasesAfterReviewerApprove(submission, reviewerEntry);

        assertThat(created.getSourceSignal()).isEqualTo("ai_error_conflict");
        assertThat(created.getStatus()).isEqualTo("open");
        assertThat(created.getReviewerVerdictEntryId()).isEqualTo(900L);
        assertThat(created.getPayload()).containsEntry("fieldFindingEntryIds", List.of(801L));
    }

    @Test
    void markReviewerDifficulty_opens_case_for_submission() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(submission());
        when(seniorReviewCaseMapper.selectByCaseKey("submission:300:reviewer_difficulty")).thenReturn(null);
        doAnswer(invocation -> {
            SeniorReviewCaseEntity entity = invocation.getArgument(0);
            entity.setId(702L);
            return 1;
        }).when(seniorReviewCaseMapper).insert(any(SeniorReviewCaseEntity.class));

        SeniorReviewCaseEntity created =
            seniorReviewCaseService.markReviewerDifficulty(SUBMISSION_ID, REVIEWER_ID, "边界样本,需要仲裁");

        assertThat(created.getSourceSignal()).isEqualTo("reviewer_difficulty");
        assertThat(created.getStatus()).isEqualTo("open");
        assertThat(created.getReviewerId()).isEqualTo(REVIEWER_ID);
        assertThat(created.getPayload()).containsEntry("reason", "边界样本,需要仲裁");
    }

    @Test
    void markReviewerDifficulty_throws_when_submission_missing() {
        when(submissionMapper.selectById(SUBMISSION_ID)).thenReturn(null);

        assertThatThrownBy(() -> seniorReviewCaseService.markReviewerDifficulty(SUBMISSION_ID, REVIEWER_ID, "疑难"))
            .isInstanceOf(SubmissionNotFoundException.class);

        verify(seniorReviewCaseMapper, never()).insert(any());
    }

    @Test
    void resolveCase_records_senior_resolution() {
        SeniorReviewCaseEntity existing = openCase(703L);
        when(seniorReviewCaseMapper.selectById(703L)).thenReturn(existing);
        when(seniorReviewCaseMapper.resolveCase(eq(703L), eq(REVIEWER_ID), eq("uphold_reviewer"), eq("同意初审"), any(), eq(NOW)))
            .thenReturn(1);
        when(seniorReviewCaseMapper.selectById(703L)).thenReturn(existing, resolvedCase(703L, "uphold_reviewer"));

        SeniorReviewCaseEntity resolved =
            seniorReviewCaseService.resolveCase(703L, REVIEWER_ID, "uphold_reviewer", "同意初审", Map.of("owner", "reviewer"));

        assertThat(resolved.getResolution()).isEqualTo("uphold_reviewer");
        verify(seniorReviewCaseMapper)
            .resolveCase(eq(703L), eq(REVIEWER_ID), eq("uphold_reviewer"), eq("同意初审"), any(), eq(NOW));
    }

    private static SubmissionEntity submission() {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(SUBMISSION_ID);
        submission.setTaskId(TASK_ID);
        submission.setLabelerId(LABELER_ID);
        submission.setStatusCode("submitted");
        return submission;
    }

    private static QualityLedgerEntryEntity aiOverallEntry(Long id, String recommendation) {
        QualityLedgerEntryEntity entry = baseEntry(id);
        entry.setEvidenceType("ai_overall_recommendation");
        entry.setActorType("ai");
        entry.setActorId(null);
        entry.setPayload(Map.of("recommendation", recommendation, "finalScore", "0.55", "scoringRuleVersion", "ai_review_rule:v1"));
        return entry;
    }

    private static QualityLedgerEntryEntity reviewerEntry(Long id, String verdict) {
        QualityLedgerEntryEntity entry = baseEntry(id);
        entry.setEvidenceType("reviewer_overall_verdict");
        entry.setActorType("reviewer");
        entry.setActorId(REVIEWER_ID);
        entry.setPayload(Map.of("verdict", verdict, "reviewLevel", "reviewer"));
        return entry;
    }

    private static QualityLedgerEntryEntity fieldFindingEntry(Long id, String severity, String confidence) {
        QualityLedgerEntryEntity entry = baseEntry(id);
        entry.setEvidenceType("ai_field_finding");
        entry.setActorType("ai");
        entry.setActorId(null);
        entry.setPayload(Map.of("severity", severity, "confidence", confidence, "finding", "关键字段错误", "fieldPath", "answer"));
        return entry;
    }

    private static QualityLedgerEntryEntity baseEntry(Long id) {
        QualityLedgerEntryEntity entry = new QualityLedgerEntryEntity();
        entry.setId(id);
        entry.setSubmissionId(SUBMISSION_ID);
        entry.setTaskId(TASK_ID);
        entry.setCreatedAt(NOW);
        return entry;
    }

    private static SeniorReviewCaseEntity openCase(Long id) {
        SeniorReviewCaseEntity entity = new SeniorReviewCaseEntity();
        entity.setId(id);
        entity.setSubmissionId(SUBMISSION_ID);
        entity.setTaskId(TASK_ID);
        entity.setCaseType("arbitration");
        entity.setSourceSignal("ai_manual_review");
        entity.setStatus("open");
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW);
        return entity;
    }

    private static SeniorReviewCaseEntity resolvedCase(Long id, String resolution) {
        SeniorReviewCaseEntity entity = openCase(id);
        entity.setStatus("resolved");
        entity.setResolution(resolution);
        entity.setResolvedAt(NOW);
        return entity;
    }
}
