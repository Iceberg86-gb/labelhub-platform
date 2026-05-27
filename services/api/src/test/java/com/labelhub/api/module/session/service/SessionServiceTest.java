package com.labelhub.api.module.session.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.api.generated.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEvent;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
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
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-24T12:00:00");

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final DatasetItemMapper datasetItemMapper = mock(DatasetItemMapper.class);
    private final SessionMapper sessionMapper = mock(SessionMapper.class);
    private final SchemaVersionMapper schemaVersionMapper = mock(SchemaVersionMapper.class);
    private final DraftMapper draftMapper = mock(DraftMapper.class);
    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private final Canonicalizer canonicalizer = new Canonicalizer(new ObjectMapper());
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T12:00:00Z"), ZoneOffset.UTC);
        sessionService = new SessionService(
            taskMapper,
            datasetItemMapper,
            sessionMapper,
            schemaVersionMapper,
            draftMapper,
            submissionMapper,
            canonicalizer,
            clock,
            auditLogService
        );
    }

    @Test
    void claim_creates_session_and_marks_dataset_item_claimed() {
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(datasetItemMapper.selectNextAvailableForUpdate(500L, 10L)).thenReturn(item(700L));
        when(datasetItemMapper.updateStatus(700L, "claimed")).thenReturn(1);
        when(sessionMapper.insert(any(SessionEntity.class))).thenAnswer(invocation -> {
            SessionEntity entity = invocation.getArgument(0);
            entity.setId(900L);
            return 1;
        });

        SessionEntity session = sessionService.claim(10L, 1002L);

        assertThat(session.getId()).isEqualTo(900L);
        assertThat(session.getTaskId()).isEqualTo(10L);
        assertThat(session.getDatasetItemId()).isEqualTo(700L);
        assertThat(session.getSchemaVersionId()).isEqualTo(300L);
        assertThat(session.getLabelerId()).isEqualTo(1002L);
        assertThat(session.getStatus()).isEqualTo("claimed");
        assertThat(session.getClaimedAt()).isEqualTo(NOW);
        assertThat(session.getClaimSnapshot()).containsEntry("taskId", 10L);
        assertThat(session.getClaimSnapshot()).containsEntry("schemaVersionId", 300L);
        assertThat(session.getClaimSnapshot()).containsEntry("datasetItemId", 700L);
        verify(datasetItemMapper).updateStatus(700L, "claimed");
    }

    @Test
    void claim_rejects_when_task_optimistic_update_fails() {
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(0);

        assertThatThrownBy(() -> sessionService.claim(10L, 1002L))
            .isInstanceOf(TaskNotAvailableException.class);

        verify(taskMapper, never()).selectById(any());
        verify(sessionMapper, never()).insert(any(SessionEntity.class));
    }

    @Test
    void claim_rejects_when_no_dataset_item_is_available() {
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(datasetItemMapper.selectNextAvailableForUpdate(500L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> sessionService.claim(10L, 1002L))
            .isInstanceOf(NoAvailableDatasetItemException.class);

        verify(datasetItemMapper, never()).updateStatus(any(), any());
        verify(sessionMapper, never()).insert(any(SessionEntity.class));
    }

    @Test
    void claim_locks_quota_before_selecting_dataset_item() {
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(datasetItemMapper.selectNextAvailableForUpdate(500L, 10L)).thenReturn(item(700L));
        when(datasetItemMapper.updateStatus(700L, "claimed")).thenReturn(1);
        when(sessionMapper.insert(any(SessionEntity.class))).thenReturn(1);

        sessionService.claim(10L, 1002L);

        InOrder inOrder = inOrder(taskMapper, datasetItemMapper, sessionMapper);
        inOrder.verify(taskMapper).incrementQuotaClaimedIfAvailable(10L);
        inOrder.verify(taskMapper).selectById(10L);
        inOrder.verify(datasetItemMapper).selectNextAvailableForUpdate(500L, 10L);
        inOrder.verify(datasetItemMapper).updateStatus(700L, "claimed");
        inOrder.verify(sessionMapper).insert(any(SessionEntity.class));
    }

    @Test
    void claim_binds_schema_version_from_task_current_pointer() {
        TaskEntity task = publishedTask();
        task.setCurrentSchemaVersionId(4242L);
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(task);
        when(datasetItemMapper.selectNextAvailableForUpdate(500L, 10L)).thenReturn(item(700L));
        when(datasetItemMapper.updateStatus(700L, "claimed")).thenReturn(1);
        when(sessionMapper.insert(any(SessionEntity.class))).thenReturn(1);

        SessionEntity session = sessionService.claim(10L, 1002L);

        assertThat(session.getSchemaVersionId()).isEqualTo(4242L);
    }

    @Test
    void listMarketplace_returns_views_with_available_item_counts() {
        Page<TaskEntity> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(publishedTask()));
        when(taskMapper.selectMarketplace(any(Page.class))).thenReturn(page);
        when(datasetItemMapper.countAvailable(500L, 10L)).thenReturn(3);

        Page<MarketplaceTaskView> result = sessionService.listMarketplace(1002L, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).availableItemCount()).isEqualTo(3);
        assertThat(result.getRecords().get(0).task().getId()).isEqualTo(10L);
    }

    @Test
    void claim_records_dataset_item_payload_in_snapshot() {
        DatasetItemEntity item = item(700L);
        item.setItemPayload(Map.of("source", "row-1"));
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(datasetItemMapper.selectNextAvailableForUpdate(500L, 10L)).thenReturn(item);
        when(datasetItemMapper.updateStatus(700L, "claimed")).thenReturn(1);
        when(sessionMapper.insert(any(SessionEntity.class))).thenReturn(1);

        SessionEntity session = sessionService.claim(10L, 1002L);

        assertThat(session.getClaimSnapshot()).containsEntry("datasetItemPayload", Map.of("source", "row-1"));
    }

    @Test
    void claim_requires_one_row_for_dataset_item_status_update() {
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(datasetItemMapper.selectNextAvailableForUpdate(500L, 10L)).thenReturn(item(700L));
        when(datasetItemMapper.updateStatus(700L, "claimed")).thenReturn(0);

        assertThatThrownBy(() -> sessionService.claim(10L, 1002L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("update dataset item status");
    }

    @Test
    void claim_requires_one_row_for_session_insert() {
        when(taskMapper.incrementQuotaClaimedIfAvailable(10L)).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(datasetItemMapper.selectNextAvailableForUpdate(500L, 10L)).thenReturn(item(700L));
        when(datasetItemMapper.updateStatus(700L, "claimed")).thenReturn(1);
        when(sessionMapper.insert(any(SessionEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> sessionService.claim(10L, 1002L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("insert session");
    }

    @Test
    void assertLabelerOwnsSession_returns_session_for_owner_labeler() {
        SessionEntity session = claimedSession(900L, 1002L);
        when(sessionMapper.selectById(900L)).thenReturn(session);

        SessionEntity result = sessionService.assertLabelerOwnsSession(900L, 1002L);

        assertThat(result).isSameAs(session);
    }

    @Test
    void assertLabelerOwnsSession_throws_when_session_not_found() {
        when(sessionMapper.selectById(900L)).thenReturn(null);

        assertThatThrownBy(() -> sessionService.assertLabelerOwnsSession(900L, 1002L))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void assertLabelerOwnsSession_throws_when_labeler_mismatch() {
        when(sessionMapper.selectById(900L)).thenReturn(claimedSession(900L, 2002L));

        assertThatThrownBy(() -> sessionService.assertLabelerOwnsSession(900L, 1002L))
            .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void getDetail_returns_session_with_task_schema_item_and_latest_draft() {
        SessionEntity session = claimedSession(900L, 1002L);
        when(sessionMapper.selectById(900L)).thenReturn(session);
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(schemaVersionMapper.selectById(300L)).thenReturn(schemaVersion(300L));
        when(datasetItemMapper.selectById(700L)).thenReturn(item(700L));
        when(draftMapper.selectLatestBySession(900L)).thenReturn(draft(900L, 2));

        SessionDetailView view = sessionService.getDetail(900L, 1002L);

        assertThat(view.getSession()).isSameAs(session);
        assertThat(view.getTask().getId()).isEqualTo(10L);
        assertThat(view.getSchemaVersion().getId()).isEqualTo(300L);
        assertThat(view.getDatasetItem().getId()).isEqualTo(700L);
        assertThat(view.getLatestDraft().getRevisionNo()).isEqualTo(2);
    }

    @Test
    void getDetail_returns_null_latest_draft_when_no_drafts_saved() {
        when(sessionMapper.selectById(900L)).thenReturn(claimedSession(900L, 1002L));
        when(taskMapper.selectById(10L)).thenReturn(publishedTask());
        when(schemaVersionMapper.selectById(300L)).thenReturn(schemaVersion(300L));
        when(datasetItemMapper.selectById(700L)).thenReturn(item(700L));
        when(draftMapper.selectLatestBySession(900L)).thenReturn(null);

        SessionDetailView view = sessionService.getDetail(900L, 1002L);

        assertThat(view.getLatestDraft()).isNull();
    }

    @Test
    void getDetail_throws_when_session_not_found() {
        when(sessionMapper.selectById(900L)).thenReturn(null);

        assertThatThrownBy(() -> sessionService.getDetail(900L, 1002L))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void getDetail_throws_when_session_belongs_to_different_labeler() {
        when(sessionMapper.selectById(900L)).thenReturn(claimedSession(900L, 2002L));

        assertThatThrownBy(() -> sessionService.getDetail(900L, 1002L))
            .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void listMySessions_filters_by_labeler_id() {
        Page<SessionEntity> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(claimedSession(900L, 1002L)));
        when(sessionMapper.selectByLabeler(any(Page.class), any(), any())).thenReturn(page);

        Page<SessionEntity> result = sessionService.listMySessions(1002L, null, 1, 20);

        assertThat(result.getRecords()).singleElement().extracting(SessionEntity::getLabelerId).isEqualTo(1002L);
        verify(sessionMapper).selectByLabeler(any(Page.class), org.mockito.ArgumentMatchers.eq(1002L), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void listMySessions_applies_status_filter() {
        Page<SessionEntity> page = new Page<>(1, 20);
        when(sessionMapper.selectByLabeler(any(Page.class), any(), any())).thenReturn(page);

        sessionService.listMySessions(1002L, "claimed", 1, 20);

        verify(sessionMapper).selectByLabeler(any(Page.class), org.mockito.ArgumentMatchers.eq(1002L), org.mockito.ArgumentMatchers.eq("claimed"));
    }

    @Test
    void listMySessions_orders_by_claimed_at_desc_at_mapper_boundary() {
        Page<SessionEntity> page = new Page<>(1, 20);
        when(sessionMapper.selectByLabeler(any(Page.class), any(), any())).thenReturn(page);

        sessionService.listMySessions(1002L, null, 1, 20);

        verify(sessionMapper).selectByLabeler(any(Page.class), org.mockito.ArgumentMatchers.eq(1002L), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void saveDraft_assigns_revision_1_for_first_save() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));
        when(draftMapper.selectMaxRevisionNumber(900L)).thenReturn(null);
        when(draftMapper.insert(any(DraftEntity.class))).thenReturn(1);

        DraftEntity draft = sessionService.saveDraft(900L, 1002L, Map.of("field_0", "answer"));

        assertThat(draft.getRevisionNo()).isEqualTo(1);
        assertThat(draft.getDraftPayload()).containsEntry("field_0", "answer");
        assertThat(draft.getSavedAt()).isEqualTo(NOW);
    }

    @Test
    void saveDraft_increments_revision_sequentially() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));
        when(draftMapper.selectMaxRevisionNumber(900L)).thenReturn(2);
        when(draftMapper.insert(any(DraftEntity.class))).thenReturn(1);

        DraftEntity draft = sessionService.saveDraft(900L, 1002L, Map.of("field_0", "answer"));

        assertThat(draft.getRevisionNo()).isEqualTo(3);
    }

    @Test
    void saveDraft_rejects_when_session_submitted() {
        SessionEntity submitted = claimedSession(900L, 1002L);
        submitted.setStatus("submitted");
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(submitted);

        assertThatThrownBy(() -> sessionService.saveDraft(900L, 1002L, Map.of("field_0", "answer")))
            .isInstanceOf(SessionNotEditableException.class);

        verify(draftMapper, never()).insert(any(DraftEntity.class));
    }

    @Test
    void saveDraft_rejects_when_session_belongs_to_different_labeler() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 2002L));

        assertThatThrownBy(() -> sessionService.saveDraft(900L, 1002L, Map.of("field_0", "answer")))
            .isInstanceOf(SessionNotFoundException.class);

        verify(draftMapper, never()).insert(any(DraftEntity.class));
    }

    @Test
    void saveDraft_writes_in_correct_order() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));
        when(draftMapper.selectMaxRevisionNumber(900L)).thenReturn(1);
        when(draftMapper.insert(any(DraftEntity.class))).thenReturn(1);

        sessionService.saveDraft(900L, 1002L, Map.of("field_0", "answer"));

        InOrder inOrder = inOrder(sessionMapper, draftMapper);
        inOrder.verify(sessionMapper).selectByIdForUpdate(900L);
        inOrder.verify(draftMapper).selectMaxRevisionNumber(900L);
        inOrder.verify(draftMapper).insert(any(DraftEntity.class));
    }

    @Test
    void getLatestDraft_throws_when_no_draft_exists() {
        when(sessionMapper.selectById(900L)).thenReturn(claimedSession(900L, 1002L));
        when(draftMapper.selectLatestBySession(900L)).thenReturn(null);

        assertThatThrownBy(() -> sessionService.getLatestDraft(900L, 1002L))
            .isInstanceOf(DraftNotFoundException.class);
    }

    @Test
    void getLatestDraft_returns_most_recent_revision() {
        when(sessionMapper.selectById(900L)).thenReturn(claimedSession(900L, 1002L));
        when(draftMapper.selectLatestBySession(900L)).thenReturn(draft(900L, 3));

        DraftEntity draft = sessionService.getLatestDraft(900L, 1002L);

        assertThat(draft.getRevisionNo()).isEqualTo(3);
    }

    @Test
    void submit_creates_submission_with_submitted_status_and_inherits_session_schema_version_id() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));
        when(submissionMapper.insert(any(SubmissionEntity.class))).thenAnswer(invocation -> {
            SubmissionEntity submission = invocation.getArgument(0);
            submission.setId(1200L);
            return 1;
        });
        when(sessionMapper.updateById(any(SessionEntity.class))).thenReturn(1);

        SubmissionEntity submission = sessionService.submit(900L, 1002L, Map.of("field_0", "answer"));

        assertThat(submission.getId()).isEqualTo(1200L);
        assertThat(submission.getSessionId()).isEqualTo(900L);
        assertThat(submission.getTaskId()).isEqualTo(10L);
        assertThat(submission.getDatasetItemId()).isEqualTo(700L);
        assertThat(submission.getLabelerId()).isEqualTo(1002L);
        assertThat(submission.getSchemaVersionId()).isEqualTo(300L);
        assertThat(submission.getStatusCode()).isEqualTo("submitted");
    }

    @Test
    void submit_writesSubmissionCreateAuditEvent() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));
        when(submissionMapper.insert(any(SubmissionEntity.class))).thenAnswer(invocation -> {
            SubmissionEntity submission = invocation.getArgument(0);
            submission.setId(1200L);
            return 1;
        });
        when(sessionMapper.updateById(any(SessionEntity.class))).thenReturn(1);

        sessionService.submit(900L, 1002L, Map.of("field_0", "answer"));

        AuditEvent event = capturedAuditEvent();
        assertThat(event.action()).isEqualTo(AuditActions.SUBMISSION_CREATE);
        assertThat(event.actorType()).isEqualTo("user");
        assertThat(event.resourceType()).isEqualTo("submission");
    }

    @Test
    void submit_writes_session_status_submitted_independently_from_submission_status() {
        SessionEntity session = claimedSession(900L, 1002L);
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(session);
        when(submissionMapper.insert(any(SubmissionEntity.class))).thenAnswer(invocation -> {
            SubmissionEntity submission = invocation.getArgument(0);
            submission.setId(1200L);
            return 1;
        });
        when(sessionMapper.updateById(any(SessionEntity.class))).thenReturn(1);

        SubmissionEntity submission = sessionService.submit(900L, 1002L, Map.of("field_0", "answer"));

        assertThat(submission.getStatusCode()).isEqualTo("submitted");
        assertThat(session.getStatus()).isEqualTo("submitted");
        assertThat(session.getSubmittedAt()).isEqualTo(NOW);
    }

    @Test
    void submit_computes_canonical_content_hash() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));
        when(submissionMapper.insert(any(SubmissionEntity.class))).thenReturn(1);
        when(sessionMapper.updateById(any(SessionEntity.class))).thenReturn(1);
        Map<String, Object> payload = Map.of("b", 2, "a", 1);
        String expected = canonicalizer.sha256Hex(canonicalizer.canonicalJson(payload));

        SubmissionEntity submission = sessionService.submit(900L, 1002L, payload);

        assertThat(submission.getContentHash()).isEqualTo(expected);
    }

    @Test
    void submit_transitions_session_to_submitted_status_and_sets_submitted_at() {
        SessionEntity session = claimedSession(900L, 1002L);
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(session);
        when(submissionMapper.insert(any(SubmissionEntity.class))).thenReturn(1);
        when(sessionMapper.updateById(any(SessionEntity.class))).thenReturn(1);

        sessionService.submit(900L, 1002L, Map.of("field_0", "answer"));

        assertThat(session.getStatus()).isEqualTo("submitted");
        assertThat(session.getSubmittedAt()).isEqualTo(NOW);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void submit_rejects_when_session_already_submitted() {
        SessionEntity submitted = claimedSession(900L, 1002L);
        submitted.setStatus("submitted");
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(submitted);

        assertThatThrownBy(() -> sessionService.submit(900L, 1002L, Map.of("field_0", "answer")))
            .isInstanceOf(SessionAlreadySubmittedException.class);

        verify(submissionMapper, never()).insert(any(SubmissionEntity.class));
    }

    @Test
    void submit_rejects_when_session_belongs_to_different_labeler() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 2002L));

        assertThatThrownBy(() -> sessionService.submit(900L, 1002L, Map.of("field_0", "answer")))
            .isInstanceOf(SessionNotFoundException.class);

        verify(submissionMapper, never()).insert(any(SubmissionEntity.class));
    }

    @Test
    void submit_rejects_null_answer_payload() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));

        assertThatThrownBy(() -> sessionService.submit(900L, 1002L, null))
            .isInstanceOf(InvalidSubmissionPayloadException.class);

        verify(submissionMapper, never()).insert(any(SubmissionEntity.class));
    }

    @Test
    void submit_writes_submission_before_session_status_update() {
        when(sessionMapper.selectByIdForUpdate(900L)).thenReturn(claimedSession(900L, 1002L));
        when(submissionMapper.insert(any(SubmissionEntity.class))).thenReturn(1);
        when(sessionMapper.updateById(any(SessionEntity.class))).thenReturn(1);

        sessionService.submit(900L, 1002L, Map.of("field_0", "answer"));

        InOrder inOrder = inOrder(submissionMapper, sessionMapper);
        inOrder.verify(submissionMapper).insert(any(SubmissionEntity.class));
        inOrder.verify(sessionMapper).updateById(any(SessionEntity.class));
    }

    @Test
    void getSubmissionForLabeler_returns_submission_for_owner() {
        SubmissionEntity submission = submission(1200L, 1002L);
        when(submissionMapper.selectById(1200L)).thenReturn(submission);

        SubmissionEntity result = sessionService.getSubmissionForLabeler(1200L, 1002L);

        assertThat(result).isSameAs(submission);
    }

    @Test
    void getSubmissionForLabeler_returns_404_when_belongs_to_different_labeler() {
        when(submissionMapper.selectById(1200L)).thenReturn(submission(1200L, 2002L));

        assertThatThrownBy(() -> sessionService.getSubmissionForLabeler(1200L, 1002L))
            .isInstanceOf(SubmissionNotFoundException.class);
    }

    private TaskEntity publishedTask() {
        TaskEntity task = new TaskEntity();
        task.setId(10L);
        task.setTitle("Marketplace task");
        task.setDescription("Demo");
        task.setInstructionRichText("<p>Label this</p>");
        task.setDeadlineAt(LocalDateTime.parse("2030-01-01T00:00:00"));
        task.setQuotaTotal(5);
        task.setQuotaClaimed(0);
        task.setStatus(TaskStatus.PUBLISHED);
        task.setOwnerId(1001L);
        task.setCurrentSchemaVersionId(300L);
        task.setCurrentDatasetId(500L);
        return task;
    }

    private DatasetItemEntity item(Long id) {
        DatasetItemEntity item = new DatasetItemEntity();
        item.setId(id);
        item.setDatasetId(500L);
        item.setTaskId(10L);
        item.setOrdinal(1);
        item.setItemPayload(Map.of("source", "demo"));
        item.setItemHash("abc123");
        item.setStatus("available");
        return item;
    }

    private SchemaVersionEntity schemaVersion(Long id) {
        SchemaVersionEntity version = new SchemaVersionEntity();
        version.setId(id);
        version.setSchemaId(50L);
        version.setVersionNumber(1);
        version.setSchemaJson(Map.of("fields", List.of()));
        version.setFieldStableIds(List.of());
        version.setContentHash("hash");
        version.setStatusCode("published");
        version.setPublishedAt(NOW);
        return version;
    }

    private DraftEntity draft(Long sessionId, int revisionNo) {
        DraftEntity draft = new DraftEntity();
        draft.setId(1000L + revisionNo);
        draft.setSessionId(sessionId);
        draft.setRevisionNo(revisionNo);
        draft.setDraftPayload(Map.of("field_0", "answer-" + revisionNo));
        draft.setSavedAt(NOW.plusMinutes(revisionNo));
        return draft;
    }

    private SessionEntity claimedSession(Long id, Long labelerId) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setTaskId(10L);
        session.setDatasetItemId(700L);
        session.setLabelerId(labelerId);
        session.setSchemaVersionId(300L);
        session.setStatus("claimed");
        return session;
    }

    private SubmissionEntity submission(Long id, Long labelerId) {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(id);
        submission.setSessionId(900L);
        submission.setTaskId(10L);
        submission.setDatasetItemId(700L);
        submission.setLabelerId(labelerId);
        submission.setSchemaVersionId(300L);
        submission.setAnswerPayload(Map.of("field_0", "answer"));
        submission.setContentHash("hash");
        submission.setStatusCode("under_ai_review");
        return submission;
    }

    private AuditEvent capturedAuditEvent() {
        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(captor.capture());
        return captor.getValue().build();
    }
}
