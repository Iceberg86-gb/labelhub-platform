package com.labelhub.api.module.submission.web;

import com.labelhub.api.generated.model.OwnerSubmissionSummary;
import com.labelhub.api.generated.model.PagedOwnerSubmissions;
import com.labelhub.api.generated.model.Submission;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.task.service.PagedResult;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class SubmissionDtoMapper {

    public Submission toSubmission(SubmissionEntity entity) {
        Submission dto = new Submission();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setTaskId(entity.getTaskId());
        dto.setDatasetItemId(entity.getDatasetItemId());
        dto.setLabelerId(entity.getLabelerId());
        dto.setSchemaVersionId(entity.getSchemaVersionId());
        dto.setAnswerPayload(entity.getAnswerPayload());
        dto.setProvenance(entity.getProvenance());
        dto.setContentHash(entity.getContentHash());
        dto.setStatus(entity.getStatusCode());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        dto.setSupersededById(entity.getSupersededById());
        return dto;
    }

    public PagedOwnerSubmissions toPagedOwnerSubmissions(PagedResult<SubmissionEntity> result) {
        PagedOwnerSubmissions dto = new PagedOwnerSubmissions();
        dto.setItems(result.items().stream().map(this::toOwnerSubmissionSummary).toList());
        dto.setTotal(result.total());
        dto.setPage((int) result.page());
        dto.setSize((int) result.size());
        return dto;
    }

    private OwnerSubmissionSummary toOwnerSubmissionSummary(SubmissionEntity entity) {
        OwnerSubmissionSummary dto = new OwnerSubmissionSummary();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setTaskId(entity.getTaskId());
        dto.setDatasetItemId(entity.getDatasetItemId());
        dto.setSchemaVersionId(entity.getSchemaVersionId());
        dto.setLabelerId(entity.getLabelerId());
        dto.setStatus(entity.getStatusCode());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
