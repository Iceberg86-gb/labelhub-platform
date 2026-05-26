package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.ai.provider.FieldFinding;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    static final String REVIEWER_OVERALL_VERDICT = "reviewer_overall_verdict";
    static final String AI_FIELD_FINDING = "ai_field_finding";

    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;
    private final Clock clock;

    public LedgerService(
        SubmissionMapper submissionMapper,
        TaskMapper taskMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Clock clock
    ) {
        this.submissionMapper = submissionMapper;
        this.taskMapper = taskMapper;
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
        this.clock = clock;
    }

    @Transactional
    public QualityLedgerEntryEntity createEntry(
        Long submissionId,
        Long reviewerUserId,
        String entryType,
        Map<String, Object> payload
    ) {
        if (!REVIEWER_OVERALL_VERDICT.equals(entryType)) {
            throw new LedgerEntryTypeNotSupportedException(entryType);
        }

        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        if (Objects.equals(submission.getLabelerId(), reviewerUserId)) {
            throw new SelfReviewNotAllowedException(submissionId);
        }

        validateReviewerOverallVerdictPayload(payload);

        QualityLedgerEntryEntity entity = new QualityLedgerEntryEntity();
        entity.setSubmissionId(submissionId);
        entity.setTaskId(submission.getTaskId());
        entity.setEvidenceType(entryType);
        entity.setActorType("reviewer");
        entity.setActorId(reviewerUserId);
        entity.setAiCallId(null);
        entity.setPayload(payload);
        entity.setCreatedAt(LocalDateTime.now(clock));
        requireOneRow(qualityLedgerEntryMapper.insert(entity), "insert quality ledger entry");
        return entity;
    }

    public PagedResult<QualityLedgerEntryEntity> listEntries(
        Long submissionId,
        Long requesterUserId,
        Set<String> requesterRoles,
        long page,
        long size
    ) {
        SubmissionEntity submission = requireReadableSubmission(submissionId, requesterUserId, requesterRoles);
        long offset = (page - 1) * size;
        List<QualityLedgerEntryEntity> items =
            qualityLedgerEntryMapper.selectBySubmissionId(submission.getId(), offset, size);
        Long total = qualityLedgerEntryMapper.selectCountBySubmissionId(submission.getId());
        return new PagedResult<>(items, total == null ? 0 : total, page, size);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public List<QualityLedgerEntryEntity> appendAiFieldFindings(
        Long submissionId,
        Long taskId,
        Long aiCallId,
        List<FieldFinding> findings
    ) {
        Objects.requireNonNull(submissionId, "submissionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(aiCallId, "aiCallId");
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<QualityLedgerEntryEntity> entries = new ArrayList<>();
        for (FieldFinding finding : findings) {
            QualityLedgerEntryEntity entity = new QualityLedgerEntryEntity();
            entity.setSubmissionId(submissionId);
            entity.setTaskId(taskId);
            entity.setEvidenceType(AI_FIELD_FINDING);
            entity.setActorType("ai");
            entity.setActorId(null);
            entity.setAiCallId(aiCallId);
            entity.setPayload(buildAiFieldFindingPayload(finding));
            entity.setCreatedAt(now);
            requireOneRow(qualityLedgerEntryMapper.insert(entity), "insert ai field finding ledger entry");
            entries.add(entity);
        }
        return entries;
    }

    private SubmissionEntity requireReadableSubmission(Long submissionId, Long requesterUserId, Set<String> requesterRoles) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        if (hasReadAccess(submission, requesterUserId, requesterRoles)) {
            return submission;
        }
        throw new SubmissionNotFoundException(submissionId);
    }

    private boolean hasReadAccess(SubmissionEntity submission, Long requesterUserId, Set<String> requesterRoles) {
        if (Objects.equals(submission.getLabelerId(), requesterUserId)) {
            return true;
        }
        if (hasRole(requesterRoles, "REVIEWER")) {
            return true;
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        return task != null && Objects.equals(task.getOwnerId(), requesterUserId);
    }

    private boolean hasRole(Set<String> roles, String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }

    private void validateReviewerOverallVerdictPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new LedgerEntryPayloadInvalidException("payload is required");
        }
        Object verdict = payload.get("verdict");
        if (!"approve".equals(verdict) && !"reject".equals(verdict)) {
            throw new LedgerEntryPayloadInvalidException("payload.verdict must be 'approve' or 'reject'");
        }
    }

    private Map<String, Object> buildAiFieldFindingPayload(FieldFinding finding) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fieldPath", finding.fieldPath());
        if (finding.stableId() != null) {
            payload.put("stableId", finding.stableId());
        }
        if (finding.label() != null) {
            payload.put("label", finding.label());
        }
        payload.put("severity", finding.severity());
        payload.put("finding", finding.finding());
        if (finding.confidence() != null) {
            payload.put("confidence", finding.confidence());
        }
        return payload;
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
