package com.labelhub.api.module.session.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.api.generated.model.DatasetItem;
import com.labelhub.api.generated.model.Draft;
import com.labelhub.api.generated.model.PagedSessions;
import com.labelhub.api.generated.model.Session;
import com.labelhub.api.generated.model.SessionDetail;
import com.labelhub.api.generated.model.SessionStatus;
import com.labelhub.api.generated.model.SessionTask;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.schema.web.SchemaDtoMapper;
import com.labelhub.api.module.session.entity.DraftEntity;
import com.labelhub.api.module.session.entity.SessionEntity;
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
        dto.setClaimSnapshot(entity.getClaimSnapshot());
        dto.setClaimedAt(entity.getClaimedAt() == null ? null : entity.getClaimedAt().atOffset(ZoneOffset.UTC));
        dto.setSubmittedAt(entity.getSubmittedAt() == null ? null : entity.getSubmittedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    public PagedSessions toPagedSessions(Page<SessionEntity> page) {
        PagedSessions dto = new PagedSessions();
        dto.setItems(page.getRecords().stream().map(this::toSession).toList());
        dto.setTotal(page.getTotal());
        dto.setPage((int) page.getCurrent());
        dto.setSize((int) page.getSize());
        return dto;
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
