package com.labelhub.api.module.session.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.api.generated.model.DatasetItem;
import com.labelhub.api.generated.model.Draft;
import com.labelhub.api.generated.model.LabelerSessionSummary;
import com.labelhub.api.generated.model.LabelerSessionWorkStatus;
import com.labelhub.api.generated.model.PagedSessions;
import com.labelhub.api.generated.model.Session;
import com.labelhub.api.generated.model.SessionDetail;
import com.labelhub.api.generated.model.SessionFinalVerdict;
import com.labelhub.api.generated.model.SessionReviewFeedback;
import com.labelhub.api.generated.model.SessionStatus;
import com.labelhub.api.generated.model.SessionTask;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.schema.web.SchemaDtoMapper;
import com.labelhub.api.module.session.entity.DraftEntity;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.service.view.LabelerSessionWorkStatusCount;
import com.labelhub.api.module.session.service.view.SessionDetailView;
import com.labelhub.api.module.task.entity.TaskEntity;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class SessionDtoMapper {

    private final SchemaDtoMapper schemaDtoMapper;

    public SessionDtoMapper(SchemaDtoMapper schemaDtoMapper) {
        this.schemaDtoMapper = schemaDtoMapper;
    }

    public Session toSession(SessionEntity entity) {
        Session dto = new Session();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setDatasetItemId(entity.getDatasetItemId());
        dto.setLabelerId(entity.getLabelerId());
        dto.setSchemaVersionId(entity.getSchemaVersionId());
        dto.setStatus(SessionStatus.fromValue(entity.getStatus()));
        dto.setWorkStatus(LabelerSessionWorkStatus.fromValue(workStatus(entity)));
        dto.setFinalVerdict(entity.getFinalVerdict() == null ? null : SessionFinalVerdict.fromValue(entity.getFinalVerdict()));
        dto.setClaimSnapshot(entity.getClaimSnapshot());
        dto.setClaimedAt(entity.getClaimedAt() == null ? null : entity.getClaimedAt().atOffset(ZoneOffset.UTC));
        dto.setSubmittedAt(entity.getSubmittedAt() == null ? null : entity.getSubmittedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    public PagedSessions toPagedSessions(Page<SessionEntity> page) {
        return toPagedSessions(page, java.util.List.of());
    }

    public PagedSessions toPagedSessions(Page<SessionEntity> page, java.util.List<LabelerSessionWorkStatusCount> counts) {
        PagedSessions dto = new PagedSessions();
        dto.setItems(page.getRecords().stream().map(this::toSession).toList());
        dto.setTotal(page.getTotal());
        dto.setPage((int) page.getCurrent());
        dto.setSize((int) page.getSize());
        dto.setSummary(toLabelerSessionSummary(counts));
        return dto;
    }

    private LabelerSessionSummary toLabelerSessionSummary(java.util.List<LabelerSessionWorkStatusCount> counts) {
        LabelerSessionSummary summary = new LabelerSessionSummary();
        summary.setSubmitted(countFor(counts, "submitted"));
        summary.setApproved(countFor(counts, "approved"));
        summary.setRejected(countFor(counts, "rejected"));
        summary.setReturnedForRevision(countFor(counts, "returned_for_revision"));
        return summary;
    }

    private Long countFor(java.util.List<LabelerSessionWorkStatusCount> counts, String workStatus) {
        return counts.stream()
            .filter(count -> workStatus.equals(count.getWorkStatus()))
            .map(LabelerSessionWorkStatusCount::getCount)
            .findFirst()
            .orElse(0L);
    }

    private String workStatus(SessionEntity entity) {
        if (entity.getWorkStatus() != null) {
            return entity.getWorkStatus();
        }
        if ("claimed".equals(entity.getStatus())) {
            return "in_progress";
        }
        return entity.getStatus();
    }

    public Draft toDraft(DraftEntity entity) {
        Draft dto = new Draft();
        dto.setSessionId(entity.getSessionId());
        dto.setRevisionNo(entity.getRevisionNo());
        dto.setPayload(entity.getDraftPayload());
        dto.setSavedAt(entity.getSavedAt() == null ? null : entity.getSavedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    public SessionDetail toSessionDetail(SessionDetailView view) {
        SessionDetail dto = new SessionDetail();
        dto.setSession(toSession(view.getSession()));
        dto.setTask(toSessionTask(view.getTask()));
        dto.setSchemaVersion(schemaDtoMapper.toSchemaVersion(view.getSchemaVersion()));
        dto.setDatasetItem(toDatasetItem(view.getDatasetItem()));
        dto.setLatestDraft(view.getLatestDraft() == null ? null : toDraft(view.getLatestDraft()));
        dto.setPreviousReviewFeedback(view.getPreviousReviewFeedback() == null
            ? null
            : toSessionReviewFeedback(view.getPreviousReviewFeedback()));
        return dto;
    }

    private SessionReviewFeedback toSessionReviewFeedback(
        com.labelhub.api.module.session.service.view.SessionReviewFeedbackView view
    ) {
        SessionReviewFeedback dto = new SessionReviewFeedback();
        dto.setLedgerEntryId(view.ledgerEntryId());
        dto.setReviewerUserId(view.reviewerUserId());
        dto.setReason(view.reason());
        dto.setCreatedAt(view.createdAt() == null ? null : view.createdAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    private DatasetItem toDatasetItem(DatasetItemEntity entity) {
        DatasetItem dto = new DatasetItem();
        dto.setId(entity.getId());
        dto.setOrdinal(entity.getOrdinal());
        dto.setItemPayload(entity.getItemPayload());
        return dto;
    }

    private SessionTask toSessionTask(TaskEntity entity) {
        SessionTask dto = new SessionTask();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setInstructionRichText(entity.getInstructionRichText());
        dto.setDeadlineAt(entity.getDeadlineAt() == null ? null : entity.getDeadlineAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
