package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.entity.SeniorReviewCaseEntity;
import com.labelhub.api.module.quality.exception.LedgerEntryPayloadInvalidException;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.mapper.SeniorReviewCaseMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMutationMapper;
import com.labelhub.api.module.session.mapper.SessionMapper;
import com.labelhub.api.module.submission.SubmissionStatusCodes;
import com.labelhub.api.module.task.service.PagedResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeniorReviewCaseService {

    public static final String CASE_TYPE_ARBITRATION = "arbitration";
    public static final String CASE_TYPE_SAMPLING = "sampling";
    public static final String SIGNAL_AI_MANUAL_REVIEW = "ai_manual_review";
    public static final String SIGNAL_AI_ERROR_CONFLICT = "ai_error_conflict";
    public static final String SIGNAL_REVIEWER_DIFFICULTY = "reviewer_difficulty";
    public static final String STATUS_PENDING_REVIEWER = "pending_reviewer";
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String RESOLUTION_UPHOLD_REVIEWER = "uphold_reviewer";
    public static final String RESOLUTION_OVERTURN_TO_REJECT = "overturn_to_reject";
    public static final String RESOLUTION_BOUNDARY_APPROVED = "boundary_approved";
    public static final String RESOLUTION_BOUNDARY_REJECTED = "boundary_rejected";

    private static final BigDecimal DEFAULT_ERROR_CONFLICT_THRESHOLD = new BigDecimal("0.85");

    private final SeniorReviewCaseMapper seniorReviewCaseMapper;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionMutationMapper submissionMutationMapper;
    private final SessionMapper sessionMapper;
    private final Clock clock;

    public SeniorReviewCaseService(
        SeniorReviewCaseMapper seniorReviewCaseMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        SubmissionMapper submissionMapper,
        SubmissionMutationMapper submissionMutationMapper,
        SessionMapper sessionMapper,
        Clock clock
    ) {
        this.seniorReviewCaseMapper = seniorReviewCaseMapper;
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
        this.submissionMapper = submissionMapper;
        this.submissionMutationMapper = submissionMutationMapper;
        this.sessionMapper = sessionMapper;
        this.clock = clock;
    }

    @Transactional
    public SeniorReviewCaseEntity recordAiOverallRecommendation(
        Long submissionId,
        Long taskId,
        QualityLedgerEntryEntity aiOverallEntry
    ) {
        if (aiOverallEntry == null || aiOverallEntry.getPayload() == null) {
            return null;
        }
        if (!"manual_review".equals(aiOverallEntry.getPayload().get("recommendation"))) {
            return null;
        }
        String caseKey = caseKey(submissionId, SIGNAL_AI_MANUAL_REVIEW);
        SeniorReviewCaseEntity existing = seniorReviewCaseMapper.selectByCaseKey(caseKey);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recommendation", aiOverallEntry.getPayload().get("recommendation"));
        payload.put("finalScore", aiOverallEntry.getPayload().get("finalScore"));
        payload.put("source", "ai_overall_recommendation");
        return insertCase(
            submissionId,
            taskId,
            caseKey,
            CASE_TYPE_ARBITRATION,
            SIGNAL_AI_MANUAL_REVIEW,
            STATUS_PENDING_REVIEWER,
            "normal",
            null,
            aiOverallEntry.getId(),
            null,
            payload
        );
    }

    @Transactional
    public SeniorReviewCaseEntity activateCasesAfterReviewerApprove(
        SubmissionEntity submission,
        QualityLedgerEntryEntity reviewerEntry
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        seniorReviewCaseMapper.openPendingCasesForReviewerApprove(
            submission.getId(),
            reviewerEntry.getId(),
            reviewerEntry.getActorId(),
            now
        );
        List<QualityLedgerEntryEntity> findings = qualityLedgerEntryMapper.selectHighConfidenceErrorFieldFindings(
            submission.getId(),
            DEFAULT_ERROR_CONFLICT_THRESHOLD
        );
        if (findings.isEmpty()) {
            return null;
        }
        String caseKey = caseKey(submission.getId(), SIGNAL_AI_ERROR_CONFLICT);
        SeniorReviewCaseEntity existing = seniorReviewCaseMapper.selectByCaseKey(caseKey);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("threshold", DEFAULT_ERROR_CONFLICT_THRESHOLD);
        payload.put("fieldFindingEntryIds", findings.stream().map(QualityLedgerEntryEntity::getId).toList());
        payload.put("fieldFindings", findings.stream().map(QualityLedgerEntryEntity::getPayload).toList());
        return insertCase(
            submission.getId(),
            submission.getTaskId(),
            caseKey,
            CASE_TYPE_ARBITRATION,
            SIGNAL_AI_ERROR_CONFLICT,
            STATUS_OPEN,
            "high",
            reviewerEntry.getId(),
            null,
            reviewerEntry.getActorId(),
            payload
        );
    }

    @Transactional
    public void cancelPendingCasesAfterReviewerReject(SubmissionEntity submission, QualityLedgerEntryEntity reviewerEntry) {
        seniorReviewCaseMapper.cancelPendingCasesForReviewerReject(
            submission.getId(),
            reviewerEntry.getId(),
            reviewerEntry.getActorId(),
            LocalDateTime.now(clock)
        );
    }

    @Transactional
    public SeniorReviewCaseEntity markReviewerDifficulty(Long submissionId, Long reviewerId, String reason) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        String caseKey = caseKey(submissionId, SIGNAL_REVIEWER_DIFFICULTY);
        SeniorReviewCaseEntity existing = seniorReviewCaseMapper.selectByCaseKey(caseKey);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason == null ? "" : reason);
        payload.put("source", "reviewer_manual_escalation");
        return insertCase(
            submissionId,
            submission.getTaskId(),
            caseKey,
            CASE_TYPE_ARBITRATION,
            SIGNAL_REVIEWER_DIFFICULTY,
            STATUS_OPEN,
            "high",
            null,
            null,
            reviewerId,
            payload
        );
    }

    @Transactional
    public SeniorReviewCaseEntity resolveCase(
        Long caseId,
        Long seniorReviewerId,
        String resolution,
        String reason,
        Map<String, Object> accountability
    ) {
        validateResolution(resolution);
        SeniorReviewCaseEntity existing = seniorReviewCaseMapper.selectById(caseId);
        if (existing == null) {
            throw new LedgerEntryPayloadInvalidException("senior review case does not exist");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requireOneRow(
            seniorReviewCaseMapper.resolveCase(caseId, seniorReviewerId, resolution, reason, accountability, now),
            "resolve senior review case"
        );
        if (isRejectingResolution(resolution)) {
            markReturnedForRevision(existing);
        }
        return seniorReviewCaseMapper.selectById(caseId);
    }

    public PagedResult<SeniorReviewCaseEntity> listOpenCases(long page, long size) {
        long offset = (page - 1) * size;
        List<SeniorReviewCaseEntity> items = seniorReviewCaseMapper.selectQueuePage(STATUS_OPEN, offset, size);
        Long total = seniorReviewCaseMapper.selectQueueCount(STATUS_OPEN);
        return new PagedResult<>(items, total == null ? 0 : total, page, size);
    }

    private SeniorReviewCaseEntity insertCase(
        Long submissionId,
        Long taskId,
        String caseKey,
        String caseType,
        String sourceSignal,
        String status,
        String priority,
        Long reviewerVerdictEntryId,
        Long aiOverallEntryId,
        Long reviewerId,
        Map<String, Object> payload
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        SeniorReviewCaseEntity entity = new SeniorReviewCaseEntity();
        entity.setSubmissionId(submissionId);
        entity.setTaskId(taskId);
        entity.setCaseKey(caseKey);
        entity.setCaseType(caseType);
        entity.setSourceSignal(sourceSignal);
        entity.setStatus(status);
        entity.setPriority(priority);
        entity.setReviewerVerdictEntryId(reviewerVerdictEntryId);
        entity.setAiOverallEntryId(aiOverallEntryId);
        entity.setReviewerId(reviewerId);
        entity.setPayload(payload);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        requireOneRow(seniorReviewCaseMapper.insert(entity), "insert senior review case");
        return entity;
    }

    private void validateResolution(String resolution) {
        if (!Objects.equals(resolution, RESOLUTION_UPHOLD_REVIEWER)
            && !Objects.equals(resolution, RESOLUTION_OVERTURN_TO_REJECT)
            && !Objects.equals(resolution, RESOLUTION_BOUNDARY_APPROVED)
            && !Objects.equals(resolution, RESOLUTION_BOUNDARY_REJECTED)) {
            throw new LedgerEntryPayloadInvalidException("invalid senior review resolution");
        }
    }

    private boolean isRejectingResolution(String resolution) {
        return RESOLUTION_OVERTURN_TO_REJECT.equals(resolution) || RESOLUTION_BOUNDARY_REJECTED.equals(resolution);
    }

    private void markReturnedForRevision(SeniorReviewCaseEntity seniorCase) {
        if (submissionMutationMapper == null) {
            return;
        }
        requireOneRow(
            submissionMutationMapper.updateStatus(seniorCase.getSubmissionId(), SubmissionStatusCodes.RETURNED_FOR_REVISION),
            "mark submission returned for revision"
        );
        if (sessionMapper == null) {
            return;
        }
        SubmissionEntity submission = submissionMapper.selectById(seniorCase.getSubmissionId());
        if (submission != null && submission.getSessionId() != null) {
            requireOneRow(
                sessionMapper.updateStatus(submission.getSessionId(), SubmissionStatusCodes.RETURNED_FOR_REVISION),
                "mark session returned for revision"
            );
        }
    }

    private String caseKey(Long submissionId, String sourceSignal) {
        return "submission:%d:%s".formatted(submissionId, sourceSignal);
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
