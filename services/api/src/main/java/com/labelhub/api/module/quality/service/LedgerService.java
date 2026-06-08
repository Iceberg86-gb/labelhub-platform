package com.labelhub.api.module.quality.service;

import com.labelhub.api.module.ai.provider.FieldFinding;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.entity.ReviewActionEntity;
import com.labelhub.api.module.quality.exception.LedgerEntryPayloadInvalidException;
import com.labelhub.api.module.quality.exception.LedgerEntryTypeNotSupportedException;
import com.labelhub.api.module.quality.exception.SelfReviewNotAllowedException;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.quality.mapper.ReviewActionMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMutationMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.session.mapper.SessionMapper;
import com.labelhub.api.module.submission.SubmissionStatusCodes;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    static final String REVIEWER_OVERALL_VERDICT = "reviewer_overall_verdict";
    static final String AI_FIELD_FINDING = "ai_field_finding";
    static final String AI_OVERALL_RECOMMENDATION = "ai_overall_recommendation";

    private final SubmissionMapper submissionMapper;
    private final SubmissionMutationMapper submissionMutationMapper;
    private final TaskMapper taskMapper;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;
    private final ReviewActionMapper reviewActionMapper;
    private final SessionMapper sessionMapper;
    private final Clock clock;
    private final AuditLogService auditLogService;

    @Autowired
    public LedgerService(
        SubmissionMapper submissionMapper,
        SubmissionMutationMapper submissionMutationMapper,
        TaskMapper taskMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        ReviewActionMapper reviewActionMapper,
        Clock clock,
        AuditLogService auditLogService,
        SessionMapper sessionMapper
    ) {
        this.submissionMapper = submissionMapper;
        this.submissionMutationMapper = submissionMutationMapper;
        this.taskMapper = taskMapper;
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
        this.reviewActionMapper = reviewActionMapper;
        this.sessionMapper = sessionMapper;
        this.clock = clock;
        this.auditLogService = auditLogService;
    }

    public LedgerService(
        SubmissionMapper submissionMapper,
        SubmissionMutationMapper submissionMutationMapper,
        TaskMapper taskMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Clock clock,
        AuditLogService auditLogService
    ) {
        this(submissionMapper, submissionMutationMapper, taskMapper, qualityLedgerEntryMapper, null, clock, auditLogService, null);
    }

    public LedgerService(
        SubmissionMapper submissionMapper,
        TaskMapper taskMapper,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Clock clock
    ) {
        this(submissionMapper, null, taskMapper, qualityLedgerEntryMapper, null, clock, AuditLogService.noop(), null);
    }

    @Transactional
    public QualityLedgerEntryEntity createEntry(
        Long submissionId,
        Long reviewerUserId,
        String entryType,
        Map<String, Object> payload
    ) {
        return createEntry(submissionId, reviewerUserId, entryType, payload, Set.of());
    }

    @Transactional
    public QualityLedgerEntryEntity createEntry(
        Long submissionId,
        Long reviewerUserId,
        String entryType,
        Map<String, Object> payload,
        Set<String> reviewerRoles
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
        String reviewLevel = reviewLevel(payload);
        requireReviewLevelAllowed(submissionId, reviewLevel, reviewerRoles);

        QualityLedgerEntryEntity entity = new QualityLedgerEntryEntity();
        entity.setSubmissionId(submissionId);
        entity.setTaskId(submission.getTaskId());
        entity.setEvidenceType(entryType);
        entity.setActorType("reviewer");
        entity.setActorId(reviewerUserId);
        entity.setAiCallId(null);
        entity.setPayload(normalizedReviewerPayload(payload, reviewLevel));
        entity.setCreatedAt(LocalDateTime.now(clock));
        requireOneRow(qualityLedgerEntryMapper.insert(entity), "insert quality ledger entry");
        String verdict = String.valueOf(payload.get("verdict"));
        if ("approve".equals(verdict)) {
            recordReviewAction(submission, entity, reviewerUserId, verdict);
            auditLogService.record(reviewAuditEvent(AuditActions.REVIEW_APPROVE, submission, entity, reviewerUserId, verdict));
        } else {
            markReturnedForRevision(submission);
            recordReviewAction(submission, entity, reviewerUserId, verdict);
            auditLogService.record(reviewAuditEvent(AuditActions.REVIEW_REJECT, submission, entity, reviewerUserId, verdict));
        }
        return entity;
    }

    private void recordReviewAction(
        SubmissionEntity submission,
        QualityLedgerEntryEntity entity,
        Long reviewerUserId,
        String verdict
    ) {
        if (reviewActionMapper == null) {
            return;
        }
        String reviewLevel = stringPayload(entity.getPayload(), "reviewLevel", "reviewer");
        String reason = stringPayload(entity.getPayload(), "reason", null);
        String toSubmissionStatus = "reject".equals(verdict)
            ? SubmissionStatusCodes.RETURNED_FOR_REVISION
            : submission.getStatusCode();

        ReviewActionEntity action = new ReviewActionEntity();
        action.setSubmissionId(submission.getId());
        action.setTaskId(submission.getTaskId());
        action.setReviewerId(reviewerUserId);
        action.setReviewLevel(reviewLevel);
        action.setAction(verdict);
        action.setStructuredReason(reason == null ? null : Map.of("reason", reason));
        action.setCommentText(reason);
        action.setRoundNo(reviewActionMapper.selectNextRoundNo(submission.getId()));
        Map<String, Object> diffSnapshot = new LinkedHashMap<>();
        diffSnapshot.put("ledgerEntryId", entity.getId());
        diffSnapshot.put("fromSubmissionStatus", submission.getStatusCode());
        diffSnapshot.put("toSubmissionStatus", toSubmissionStatus);
        diffSnapshot.put("reviewerVerdict", verdict);
        diffSnapshot.put("reviewLevel", reviewLevel);
        action.setDiffSnapshot(diffSnapshot);
        action.setCreatedAt(entity.getCreatedAt());
        requireOneRow(reviewActionMapper.insert(action), "insert review action");
    }

    private String stringPayload(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        return value instanceof String text && !text.isBlank() ? text : defaultValue;
    }

    private void markReturnedForRevision(SubmissionEntity submission) {
        requireOneRow(
            submissionMutationMapper.updateStatus(submission.getId(), SubmissionStatusCodes.RETURNED_FOR_REVISION),
            "mark submission returned for revision"
        );
        if (sessionMapper != null && submission.getSessionId() != null) {
            requireOneRow(
                sessionMapper.updateStatus(submission.getSessionId(), SubmissionStatusCodes.RETURNED_FOR_REVISION),
                "mark session returned for revision"
            );
        }
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

    @Transactional(propagation = Propagation.REQUIRED)
    public QualityLedgerEntryEntity appendAiOverallRecommendation(
        Long submissionId,
        Long taskId,
        Long aiCallId,
        Map<String, Object> payload
    ) {
        Objects.requireNonNull(submissionId, "submissionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(aiCallId, "aiCallId");
        validateAiOverallRecommendationPayload(payload);

        QualityLedgerEntryEntity entity = new QualityLedgerEntryEntity();
        entity.setSubmissionId(submissionId);
        entity.setTaskId(taskId);
        entity.setEvidenceType(AI_OVERALL_RECOMMENDATION);
        entity.setActorType("ai");
        entity.setActorId(null);
        entity.setAiCallId(aiCallId);
        entity.setPayload(payload);
        entity.setCreatedAt(LocalDateTime.now(clock));
        requireOneRow(qualityLedgerEntryMapper.insert(entity), "insert ai overall recommendation ledger entry");
        return entity;
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
        if (hasRole(requesterRoles, "REVIEWER") || hasRole(requesterRoles, "SENIOR_REVIEWER")) {
            return true;
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        return task != null && Objects.equals(task.getOwnerId(), requesterUserId);
    }

    private boolean hasRole(Set<String> roles, String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }

    private AuditEventBuilder reviewAuditEvent(
        String action,
        SubmissionEntity submission,
        QualityLedgerEntryEntity entity,
        Long reviewerUserId,
        String verdict
    ) {
        return AuditEventBuilder.forAction(action)
            .actorUser(reviewerUserId)
            .resource("submission", submission.getId())
            .payload("submissionId", submission.getId())
            .payload("taskId", submission.getTaskId())
            .payload("ledgerEntryId", entity.getId())
            .payload("reviewerUserId", reviewerUserId)
            .payload("reviewLevel", stringPayload(entity.getPayload(), "reviewLevel", ReviewLevels.REVIEWER))
            .payload("verdict", verdict);
    }

    private void validateReviewerOverallVerdictPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new LedgerEntryPayloadInvalidException("payload is required");
        }
        Object verdict = payload.get("verdict");
        if (!"approve".equals(verdict) && !"reject".equals(verdict)) {
            throw new LedgerEntryPayloadInvalidException("payload.verdict must be 'approve' or 'reject'");
        }
        Object reviewLevel = payload.get("reviewLevel");
        if (reviewLevel != null && !ReviewLevels.isValid(String.valueOf(reviewLevel))) {
            throw new LedgerEntryPayloadInvalidException("payload.reviewLevel must be 'reviewer' or 'senior_reviewer'");
        }
        if ("reject".equals(verdict)) {
            Object reason = payload.get("reason");
            if (!(reason instanceof String text) || text.isBlank()) {
                throw new LedgerEntryPayloadInvalidException("payload.reason is required when verdict is 'reject'");
            }
        }
    }

    private String reviewLevel(Map<String, Object> payload) {
        return ReviewLevels.normalize(stringPayload(payload, "reviewLevel", ReviewLevels.REVIEWER));
    }

    private Map<String, Object> normalizedReviewerPayload(Map<String, Object> payload, String reviewLevel) {
        Map<String, Object> normalized = new LinkedHashMap<>(payload);
        normalized.put("reviewLevel", reviewLevel);
        return normalized;
    }

    private void requireReviewLevelAllowed(Long submissionId, String reviewLevel, Set<String> reviewerRoles) {
        if (!ReviewLevels.SENIOR_REVIEWER.equals(reviewLevel)) {
            return;
        }
        if (!hasRole(reviewerRoles, "SENIOR_REVIEWER")) {
            throw new AccessDeniedException("SENIOR_REVIEWER role is required for senior review");
        }
        QualityLedgerEntryEntity reviewerVerdict =
            qualityLedgerEntryMapper.selectLatestReviewerOverallVerdictByReviewLevel(submissionId, ReviewLevels.REVIEWER);
        if (reviewerVerdict == null
            || reviewerVerdict.getPayload() == null
            || !"approve".equals(reviewerVerdict.getPayload().get("verdict"))) {
            throw new LedgerEntryPayloadInvalidException("senior review requires reviewer approval");
        }
    }

    private void validateAiOverallRecommendationPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new LedgerEntryPayloadInvalidException("payload is required");
        }
        Object recommendation = payload.get("recommendation");
        if (!"pass".equals(recommendation) && !"reject".equals(recommendation) && !"manual_review".equals(recommendation)) {
            throw new LedgerEntryPayloadInvalidException(
                "payload.recommendation must be 'pass', 'reject', or 'manual_review'"
            );
        }
        if (payload.get("finalScore") == null) {
            throw new LedgerEntryPayloadInvalidException("payload.finalScore is required");
        }
        if (payload.get("scoringRuleVersion") == null) {
            throw new LedgerEntryPayloadInvalidException("payload.scoringRuleVersion is required");
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
