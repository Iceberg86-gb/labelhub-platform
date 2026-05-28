package com.labelhub.api.module.session.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SchemaVersionNotFoundException;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
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
import com.labelhub.api.module.session.service.view.MarketplaceTaskView;
import com.labelhub.api.module.session.service.view.SessionDetailView;
import com.labelhub.api.module.submission.SubmissionStatusCodes;
import com.labelhub.api.module.submission.validation.AnswerPayloadValidator;
import com.labelhub.api.module.submission.validation.AnswerValidationError;
import com.labelhub.api.module.submission.validation.AnswerValidationException;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final String SESSION_CLAIMED = "claimed";
    private static final String SESSION_SUBMITTED = "submitted";
    private static final String ITEM_CLAIMED = "claimed";

    private final TaskMapper taskMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final SessionMapper sessionMapper;
    private final SchemaVersionMapper schemaVersionMapper;
    private final DraftMapper draftMapper;
    private final SubmissionMapper submissionMapper;
    private final Canonicalizer canonicalizer;
    private final Clock clock;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final AnswerPayloadValidator answerPayloadValidator;

    @Autowired
    public SessionService(
        TaskMapper taskMapper,
        DatasetItemMapper datasetItemMapper,
        SessionMapper sessionMapper,
        SchemaVersionMapper schemaVersionMapper,
        DraftMapper draftMapper,
        SubmissionMapper submissionMapper,
        Canonicalizer canonicalizer,
        Clock clock,
        AuditLogService auditLogService,
        ObjectMapper objectMapper,
        AnswerPayloadValidator answerPayloadValidator
    ) {
        this.taskMapper = taskMapper;
        this.datasetItemMapper = datasetItemMapper;
        this.sessionMapper = sessionMapper;
        this.schemaVersionMapper = schemaVersionMapper;
        this.draftMapper = draftMapper;
        this.submissionMapper = submissionMapper;
        this.canonicalizer = canonicalizer;
        this.clock = clock;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.answerPayloadValidator = answerPayloadValidator;
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
        this(taskMapper, datasetItemMapper, sessionMapper, schemaVersionMapper, draftMapper, submissionMapper,
            canonicalizer, clock, auditLogService, new ObjectMapper(), new AnswerPayloadValidator());
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
        Page<TaskEntity> tasks = (Page<TaskEntity>) taskMapper.selectMarketplace(Page.of(page, size));
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

    public SessionDetailView getDetail(Long sessionId, Long labelerId) {
        SessionEntity session = assertLabelerOwnsSession(sessionId, labelerId);
        TaskEntity task = taskMapper.selectById(session.getTaskId());
        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(session.getSchemaVersionId());
        DatasetItemEntity item = datasetItemMapper.selectById(session.getDatasetItemId());
        DraftEntity latestDraft = draftMapper.selectLatestBySession(sessionId);
        return new SessionDetailView(session, task, schemaVersion, item, latestDraft);
    }

    public Page<SessionEntity> listMySessions(Long labelerId, String statusFilter, long page, long size) {
        return (Page<SessionEntity>) sessionMapper.selectByLabeler(Page.of(page, size), labelerId, statusFilter);
    }

    @Transactional
    public DraftEntity saveDraft(Long sessionId, Long labelerId, Map<String, Object> payload) {
        SessionEntity session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || !Objects.equals(session.getLabelerId(), labelerId)) {
            throw new SessionNotFoundException(sessionId);
        }
        if (!SESSION_CLAIMED.equals(session.getStatus())) {
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
        if (session == null || !Objects.equals(session.getLabelerId(), labelerId)) {
            throw new SessionNotFoundException(sessionId);
        }
        if (!SESSION_CLAIMED.equals(session.getStatus())) {
            throw new SessionAlreadySubmittedException(sessionId, session.getStatus());
        }
        if (answerPayload == null) {
            throw new InvalidSubmissionPayloadException("answerPayload is required");
        }
        validateAnswerPayload(session, answerPayload);

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

    private void validateAnswerPayload(SessionEntity session, Map<String, Object> answerPayload) {
        SchemaVersionEntity schemaVersion = schemaVersionMapper.selectById(session.getSchemaVersionId());
        if (schemaVersion == null) {
            throw new SchemaVersionNotFoundException(session.getSchemaVersionId());
        }
        SchemaDocument schemaDocument = objectMapper.convertValue(schemaVersion.getSchemaJson(), SchemaDocument.class);
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

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
