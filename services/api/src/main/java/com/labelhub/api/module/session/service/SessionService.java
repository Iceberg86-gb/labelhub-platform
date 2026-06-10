package com.labelhub.api.module.session.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.outbox.service.OutboxEventService;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.QualityLedgerEntryMapper;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SchemaVersionNotFoundException;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMutationMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.schema.runtime.SchemaRuntimeAdapter;
import com.labelhub.api.module.session.entity.DraftEntity;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.exception.DraftNotFoundException;
import com.labelhub.api.module.session.exception.InvalidSubmissionPayloadException;
import com.labelhub.api.module.session.exception.NoAvailableDatasetItemException;
import com.labelhub.api.module.session.exception.SessionAccessDeniedException;
import com.labelhub.api.module.session.exception.SessionAlreadySubmittedException;
import com.labelhub.api.module.session.exception.SessionNotEditableException;
import com.labelhub.api.module.session.exception.SessionNotFoundException;
import com.labelhub.api.module.session.exception.TaskNotAvailableException;
import com.labelhub.api.module.session.mapper.DraftMapper;
import com.labelhub.api.module.session.mapper.SessionMapper;
import com.labelhub.api.module.session.service.view.ClaimBatchResultView;
import com.labelhub.api.module.session.service.view.BatchSubmitResultView;
import com.labelhub.api.module.session.service.view.MarketplaceTaskView;
import com.labelhub.api.module.session.service.view.MarketplaceTaskFilter;
import com.labelhub.api.module.session.service.view.LabelerSessionWorkStatusCount;
import com.labelhub.api.module.session.service.view.SessionDetailView;
import com.labelhub.api.module.session.service.view.SessionReviewFeedbackView;
import com.labelhub.api.module.submission.SubmissionStatusCodes;
import com.labelhub.api.module.submission.validation.AnswerPayloadValidator;
import com.labelhub.api.module.submission.validation.AnswerValidationError;
import com.labelhub.api.module.submission.validation.AnswerValidationException;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final String SESSION_CLAIMED = "claimed";
    private static final String SESSION_SUBMITTED = "submitted";
    private static final String SESSION_RETURNED_FOR_REVISION = "returned_for_revision";
    private static final String ITEM_CLAIMED = "claimed";
    private static final int MAX_CLAIM_BATCH_SIZE = 1000;

    private final TaskMapper taskMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final SessionMapper sessionMapper;
    private final SchemaVersionMapper schemaVersionMapper;
    private final DraftMapper draftMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionMutationMapper submissionMutationMapper;
    private final OutboxEventService outboxEventService;
    private final QualityLedgerEntryMapper qualityLedgerEntryMapper;
    private final Canonicalizer canonicalizer;
    private final Clock clock;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final AnswerPayloadValidator answerPayloadValidator;
    private final SchemaRuntimeAdapter schemaRuntimeAdapter;

    @Autowired
    public SessionService(
        TaskMapper taskMapper,
        DatasetItemMapper datasetItemMapper,
        SessionMapper sessionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DraftMapper draftMapper,
        SubmissionMapper submissionMapper,
        SubmissionMutationMapper submissionMutationMapper,
        OutboxEventService outboxEventService,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Canonicalizer canonicalizer,
        Clock clock,
        AuditLogService auditLogService,
        ObjectMapper objectMapper,
        AnswerPayloadValidator answerPayloadValidator,
        SchemaRuntimeAdapter schemaRuntimeAdapter
    ) {
        this.taskMapper = taskMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.sessionMapper = sessionMapper;
        this.schemaVersionMapper = schemaVersionMapper;
        this.draftMapper = draftMapper;
        this.submissionMapper = submissionMapper;
        this.submissionMutationMapper = submissionMutationMapper;
        this.outboxEventService = outboxEventService;
        this.qualityLedgerEntryMapper = qualityLedgerEntryMapper;
        this.canonicalizer = canonicalizer;
        this.clock = clock;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.answerPayloadValidator = answerPayloadValidator;
        this.schemaRuntimeAdapter = schemaRuntimeAdapter;
    }

    public SessionService(
        TaskMapper taskMapper,
        DatasetItemMapper datasetItemMapper,
        SessionMapper sessionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DraftMapper draftMapper,
        SubmissionMapper submissionMapper,
        SubmissionMutationMapper submissionMutationMapper,
        OutboxEventService outboxEventService,
        QualityLedgerEntryMapper qualityLedgerEntryMapper,
        Canonicalizer canonicalizer,
        Clock clock,
        AuditLogService auditLogService
    ) {
        this(taskMapper, datasetItemMapper, sessionMapper, schemaVersionMapper, draftMapper, submissionMapper,
            submissionMutationMapper, outboxEventService, qualityLedgerEntryMapper, canonicalizer, clock,
            auditLogService, new ObjectMapper(), new AnswerPayloadValidator(), new SchemaRuntimeAdapter(new ObjectMapper()));
    }

    public SessionService(
        TaskMapper taskMapper,
        DatasetItemMapper datasetItemMapper,
        SessionMapper sessionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DraftMapper draftMapper,
        SubmissionMapper submissionMapper,
        OutboxEventService outboxEventService,
        Canonicalizer canonicalizer,
        Clock clock,
        AuditLogService auditLogService
    ) {
        this(taskMapper, datasetItemMapper, sessionMapper, schemaVersionMapper, draftMapper, submissionMapper,
            null, outboxEventService, null, canonicalizer, clock, auditLogService, new ObjectMapper(),
            new AnswerPayloadValidator(), new SchemaRuntimeAdapter(new ObjectMapper()));
    }

    public SessionService(
        TaskMapper taskMapper,
        DatasetItemMapper datasetItemMapper,
        SessionMapper sessionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DraftMapper draftMapper,
        SubmissionMapper submissionMapper,
        Canonicalizer canonicalizer,
        Clock clock,
        AuditLogService auditLogService
    ) {
        this(taskMapper, datasetItemMapper, sessionMapper, schemaVersionMapper, draftMapper, submissionMapper, null,
            null, null, canonicalizer, clock, auditLogService, new ObjectMapper(), new AnswerPayloadValidator(),
            new SchemaRuntimeAdapter(new ObjectMapper()));
    }

    public SessionService(
        TaskMapper taskMapper,
        DatasetItemMapper datasetItemMapper,
        SessionMapper sessionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DraftMapper draftMapper,
        SubmissionMapper submissionMapper,
        Canonicalizer canonicalizer,
        Clock clock
    ) {
        this(taskMapper, datasetItemMapper, sessionMapper, schemaVersionMapper, draftMapper, submissionMapper,
            canonicalizer, clock, AuditLogService.noop());
    }

    public Page<MarketplaceTaskView> listMarketplace(Long labelerId, long page, long size) {
        return listMarketplace(labelerId, page, size, MarketplaceTaskFilter.empty());
    }

    public Page<MarketplaceTaskView> listMarketplace(Long labelerId, long page, long size, MarketplaceTaskFilter filter) {
        Page<TaskEntity> tasks = (Page<TaskEntity>) taskMapper.selectMarketplace(Page.of(page, size), filter);
        Page<MarketplaceTaskView> result = Page.of(tasks.getCurrent(), tasks.getSize());
        result.setTotal(tasks.getTotal());
        result.setRecords(tasks.getRecords().stream()
            .map(task -> new MarketplaceTaskView(
                task,
                datasetItemMapper.countAvailable(task.getCurrentDatasetId(), task.getId())
            ))
            .toList());
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionEntity claim(Long taskId, Long labelerId) {
        int quotaRows = taskMapper.incrementQuotaClaimedIfAvailable(taskId);
        if (quotaRows != 1) {
            throw new TaskNotAvailableException(taskId);
        }

        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || task.getCurrentDatasetId() == null || task.getCurrentSchemaVersionId() == null) {
            throw new TaskNotAvailableException(taskId);
        }

        DatasetItemEntity item = datasetItemMapper.selectNextAvailableForUpdate(task.getCurrentDatasetId(), taskId);
        if (item == null) {
            throw new NoAvailableDatasetItemException(taskId, task.getCurrentDatasetId());
        }
        requireOneRow(datasetItemMapper.updateStatus(item.getId(), ITEM_CLAIMED), "update dataset item status");

        SessionEntity session = new SessionEntity();
        session.setTaskId(taskId);
        session.setDatasetItemId(item.getId());
        session.setLabelerId(labelerId);
        session.setSchemaVersionId(task.getCurrentSchemaVersionId());
        session.setStatus(SESSION_CLAIMED);
        session.setClaimedAt(LocalDateTime.now(clock));
        session.setClaimSnapshot(claimSnapshot(task, item));
        requireOneRow(sessionMapper.insert(session), "insert session");
        return session;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClaimBatchResultView claimBatch(Long taskId, Long labelerId, Integer requestedSize) {
        int requested = requestedSize == null ? 1 : Math.max(1, requestedSize);
        int cappedRequest = Math.min(requested, MAX_CLAIM_BATCH_SIZE);
        TaskEntity task = taskMapper.selectByIdForUpdate(taskId);
        if (!isClaimableTask(task)) {
            throw new TaskNotAvailableException(taskId);
        }

        // Cap the batch to the task's remaining quota. The task row is locked FOR UPDATE above, so
        // quota_claimed cannot change under us; without this cap claimBatch would overshoot
        // quota_total whenever more dataset items are available than the quota allows (ADR-007).
        int remainingQuota = remainingQuota(task);
        if (remainingQuota <= 0) {
            throw new TaskNotAvailableException(taskId);
        }
        int effectiveRequest = Math.min(cappedRequest, remainingQuota);

        List<DatasetItemEntity> items = datasetItemMapper.selectAvailableForUpdate(task.getCurrentDatasetId(), taskId, effectiveRequest);
        if (items.isEmpty()) {
            throw new NoAvailableDatasetItemException(taskId, task.getCurrentDatasetId());
        }

        if (taskMapper.incrementQuotaClaimedBy(taskId, items.size()) != 1) {
            // Guard SQL refused the increment (would exceed quota_total) — surface a clean
            // business error instead of a partial/invalid claim.
            throw new TaskNotAvailableException(taskId);
        }
        List<SessionEntity> sessions = new ArrayList<>(items.size());
        for (DatasetItemEntity item : items) {
            requireOneRow(datasetItemMapper.updateStatus(item.getId(), ITEM_CLAIMED), "update dataset item status");
            SessionEntity session = createClaimedSession(task, item, labelerId);
            requireOneRow(sessionMapper.insert(session), "insert session");
            sessions.add(session);
        }
        return new ClaimBatchResultView(requested, sessions);
    }

    public SessionEntity assertLabelerOwnsSession(Long sessionId, Long labelerId) {
        SessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        if (!Objects.equals(session.getLabelerId(), labelerId)) {
            throw new SessionAccessDeniedException(sessionId, labelerId);
        }
        return session;
    }

    public SessionEntity assertSessionVisible(Long sessionId, Long requesterUserId, Set<String> requesterRoles) {
        SessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        boolean isLabeler = Objects.equals(session.getLabelerId(), requesterUserId);
        boolean isReviewer = hasRole(requesterRoles, "REVIEWER") || hasRole(requesterRoles, "SENIOR_REVIEWER");
        if (isLabeler || isReviewer) {
            return session;
        }

        TaskEntity task = taskMapper.selectById(session.getTaskId());
        boolean isOwner = task != null && Objects.equals(task.getOwnerId(), requesterUserId);
        if (!isOwner) {
            throw new SessionAccessDeniedException(sessionId, requesterUserId);
        }
        return session;
    }

    public SessionDetailView getDetail(Long sessionId, Long labelerId) {
        SessionEntity session = assertLabelerOwnsSession(sessionId, labelerId);
        TaskEntity task = taskMapper.selectById(session.getTaskId());
        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(session.getSchemaVersionId());
        DatasetItemEntity item = datasetItemMapper.selectById(session.getDatasetItemId());
        DraftEntity latestDraft = draftMapper.selectLatestBySession(sessionId);
        return new SessionDetailView(
            session,
            task,
            schemaVersion,
            item,
            latestDraft,
            latestReviewFeedback(sessionId)
        );
    }

    public Page<SessionEntity> listMySessions(Long labelerId, String statusFilter, long page, long size) {
        return listMySessions(labelerId, statusFilter, null, page, size);
    }

    public Page<SessionEntity> listMySessions(
        Long labelerId,
        String statusFilter,
        String workStatusFilter,
        long page,
        long size
    ) {
        return (Page<SessionEntity>) sessionMapper.selectByLabeler(
            Page.of(page, size),
            labelerId,
            statusFilter,
            workStatusFilter
        );
    }

    public List<LabelerSessionWorkStatusCount> listMySessionWorkStatusCounts(Long labelerId) {
        return sessionMapper.selectLabelerWorkStatusCounts(labelerId);
    }

    @Transactional
    public DraftEntity saveDraft(Long sessionId, Long labelerId, Map<String, Object> payload) {
        SessionEntity session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || !Objects.equals(session.getLabelerId(), labelerId)) {
            throw new SessionNotFoundException(sessionId);
        }
        if (!isEditableSessionStatus(session.getStatus())) {
            throw new SessionNotEditableException(sessionId, session.getStatus());
        }

        Integer maxRevision = draftMapper.selectMaxRevisionNumber(sessionId);
        int nextRevision = maxRevision == null ? 1 : maxRevision + 1;

        DraftEntity draft = new DraftEntity();
        draft.setSessionId(sessionId);
        draft.setRevisionNo(nextRevision);
        draft.setDraftPayload(payload);
        draft.setSavedAt(LocalDateTime.now(clock));
        requireOneRow(draftMapper.insert(draft), "insert draft");
        return draft;
    }

    public DraftEntity getLatestDraft(Long sessionId, Long labelerId) {
        assertLabelerOwnsSession(sessionId, labelerId);
        DraftEntity draft = draftMapper.selectLatestBySession(sessionId);
        if (draft == null) {
            throw new DraftNotFoundException(sessionId);
        }
        return draft;
    }

    @Transactional
    public SubmissionEntity submit(Long sessionId, Long labelerId, Map<String, Object> answerPayload) {
        SessionEntity session = sessionMapper.selectByIdForUpdate(sessionId);
        return submitLockedSession(session, sessionId, labelerId, answerPayload);
    }

    @Transactional
    public BatchSubmitResultView submitTaskDrafts(
        Long taskId,
        Long labelerId,
        Long currentSessionId,
        Map<String, Object> currentAnswerPayload
    ) {
        List<SessionEntity> sessions = sessionMapper.selectEditableByTaskAndLabelerForUpdate(taskId, labelerId);
        boolean currentSessionFound = sessions.stream().anyMatch(session -> Objects.equals(session.getId(), currentSessionId));
        if (!currentSessionFound) {
            throw new SessionNotFoundException(currentSessionId);
        }

        List<SubmissionEntity> submissions = new ArrayList<>(sessions.size());
        for (SessionEntity session : sessions) {
            Map<String, Object> payload = Objects.equals(session.getId(), currentSessionId)
                ? currentAnswerPayload
                : latestDraftPayload(session.getId());
            submissions.add(submitLockedSession(session, session.getId(), labelerId, payload));
        }
        return new BatchSubmitResultView(submissions);
    }

    private Map<String, Object> latestDraftPayload(Long sessionId) {
        DraftEntity draft = draftMapper.selectLatestBySession(sessionId);
        if (draft == null) {
            throw new DraftNotFoundException(sessionId);
        }
        return draft.getDraftPayload();
    }

    private SubmissionEntity submitLockedSession(
        SessionEntity session,
        Long sessionId,
        Long labelerId,
        Map<String, Object> answerPayload
    ) {
        if (session == null || !Objects.equals(session.getLabelerId(), labelerId)) {
            throw new SessionNotFoundException(sessionId);
        }
        if (!isEditableSessionStatus(session.getStatus())) {
            throw new SessionAlreadySubmittedException(sessionId, session.getStatus());
        }
        if (answerPayload == null) {
            throw new InvalidSubmissionPayloadException("answerPayload is required");
        }
        validateAnswerPayload(session, answerPayload);

        SubmissionEntity previousReturnedSubmission = SESSION_RETURNED_FOR_REVISION.equals(session.getStatus())
            ? submissionMapper.selectLatestBySessionIdForUpdate(sessionId)
            : null;

        SubmissionEntity submission = new SubmissionEntity();
        submission.setSessionId(sessionId);
        submission.setTaskId(session.getTaskId());
        submission.setDatasetItemId(session.getDatasetItemId());
        submission.setLabelerId(labelerId);
        submission.setSchemaVersionId(session.getSchemaVersionId());
        submission.setAnswerPayload(answerPayload);
        submission.setProvenance(null);
        submission.setContentHash(canonicalizer.sha256Hex(canonicalizer.canonicalJson(answerPayload)));
        submission.setStatusCode(SubmissionStatusCodes.SUBMITTED);
        submission.setCreatedAt(LocalDateTime.now(clock));
        requireOneRow(submissionMapper.insert(submission), "insert submission");
        if (previousReturnedSubmission != null) {
            requireOneRow(
                submissionMutationMapper.updateSupersededBy(previousReturnedSubmission.getId(), submission.getId()),
                "supersede previous returned submission"
            );
            auditLogService.record(
                AuditEventBuilder.forAction(AuditActions.SUBMISSION_SUPERSEDE)
                    .actorUser(labelerId)
                    .resource("submission", previousReturnedSubmission.getId())
                    .payload("previousSubmissionId", previousReturnedSubmission.getId())
                    .payload("newSubmissionId", submission.getId())
                    .payload("sessionId", sessionId)
                    .payload("taskId", submission.getTaskId())
                    .payload("fromStatus", previousReturnedSubmission.getStatusCode())
                    .payload("toStatus", submission.getStatusCode())
            );
        }
        Long aiReviewRuleId = currentAiReviewRuleId(session.getTaskId());
        enqueueAiReview(submission, aiReviewRuleId);

        session.setStatus(SESSION_SUBMITTED);
        session.setSubmittedAt(LocalDateTime.now(clock));
        requireOneRow(sessionMapper.updateById(session), "update session status");
        auditLogService.record(
            AuditEventBuilder.forAction(AuditActions.SUBMISSION_CREATE)
                .actorUser(labelerId)
                .resource("submission", submission.getId())
                .payload("submissionId", submission.getId())
                .payload("sessionId", sessionId)
                .payload("taskId", submission.getTaskId())
                .payload("datasetItemId", submission.getDatasetItemId())
                .payload("schemaVersionId", submission.getSchemaVersionId())
                .payload("contentHash", submission.getContentHash())
        );
        return submission;
    }

    private Long currentAiReviewRuleId(Long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        return task == null ? null : task.getCurrentAiReviewRuleId();
    }

    private boolean isEditableSessionStatus(String status) {
        return SESSION_CLAIMED.equals(status) || SESSION_RETURNED_FOR_REVISION.equals(status);
    }

    private SessionReviewFeedbackView latestReviewFeedback(Long sessionId) {
        if (qualityLedgerEntryMapper == null) {
            return null;
        }
        QualityLedgerEntryEntity entry = qualityLedgerEntryMapper.selectLatestReviewerRejectBySessionId(sessionId);
        if (entry == null) {
            return null;
        }
        Object reason = entry.getPayload() == null ? null : entry.getPayload().get("reason");
        return new SessionReviewFeedbackView(
            entry.getId(),
            entry.getActorId(),
            reason instanceof String text ? text : "",
            entry.getCreatedAt()
        );
    }

    private void enqueueAiReview(SubmissionEntity submission, Long aiReviewRuleId) {
        if (outboxEventService != null) {
            outboxEventService.enqueueSubmissionAiReview(submission, aiReviewRuleId);
        }
    }

    private void validateAnswerPayload(SessionEntity session, Map<String, Object> answerPayload) {
        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(session.getSchemaVersionId());
        if (schemaVersion == null) {
            throw new SchemaVersionNotFoundException(session.getSchemaVersionId());
        }
        SchemaDocument schemaDocument = schemaRuntimeAdapter.toSchemaDocument(schemaVersion.getSchemaJson());
        List<AnswerValidationError> errors = answerPayloadValidator.validate(schemaDocument, answerPayload);
        if (!errors.isEmpty()) {
            throw new AnswerValidationException(errors);
        }
    }

    public SubmissionEntity getSubmissionForLabeler(Long submissionId, Long labelerId) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null || !Objects.equals(submission.getLabelerId(), labelerId)) {
            throw new SubmissionNotFoundException(submissionId);
        }
        return submission;
    }

    private Map<String, Object> claimSnapshot(TaskEntity task, DatasetItemEntity item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("taskId", task.getId());
        snapshot.put("title", task.getTitle());
        snapshot.put("schemaVersionId", task.getCurrentSchemaVersionId());
        snapshot.put("datasetId", task.getCurrentDatasetId());
        snapshot.put("datasetItemId", item.getId());
        snapshot.put("datasetItemOrdinal", item.getOrdinal());
        snapshot.put("datasetItemPayload", item.getItemPayload());
        snapshot.put("claimedAt", LocalDateTime.now(clock).toString());
        return snapshot;
    }

    private SessionEntity createClaimedSession(TaskEntity task, DatasetItemEntity item, Long labelerId) {
        SessionEntity session = new SessionEntity();
        session.setTaskId(task.getId());
        session.setDatasetItemId(item.getId());
        session.setLabelerId(labelerId);
        session.setSchemaVersionId(task.getCurrentSchemaVersionId());
        session.setStatus(SESSION_CLAIMED);
        session.setClaimedAt(LocalDateTime.now(clock));
        session.setClaimSnapshot(claimSnapshot(task, item));
        return session;
    }

    private boolean isClaimableTask(TaskEntity task) {
        return task != null
            && "published".equals(task.getStatusCode())
            && task.getCurrentDatasetId() != null
            && task.getCurrentSchemaVersionId() != null
            && task.getDeadlineAt() != null
            && task.getDeadlineAt().isAfter(LocalDateTime.now(clock));
    }

    private static int remainingQuota(TaskEntity task) {
        int total = task.getQuotaTotal() == null ? 0 : task.getQuotaTotal();
        int claimed = task.getQuotaClaimed() == null ? 0 : task.getQuotaClaimed();
        return Math.max(0, total - claimed);
    }

    private boolean hasRole(Set<String> roles, String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
