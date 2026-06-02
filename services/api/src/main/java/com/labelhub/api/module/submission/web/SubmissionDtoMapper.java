package com.labelhub.api.module.submission.web;

import com.labelhub.api.generated.model.OwnerSubmissionSummary;
import com.labelhub.api.generated.model.PagedOwnerSubmissions;
import com.labelhub.api.generated.model.PrereviewSignals;
import com.labelhub.api.generated.model.PrereviewStatus;
import com.labelhub.api.generated.model.Submission;
import com.labelhub.api.module.ai.prereview.AiPrereviewSignalsView;
import com.labelhub.api.module.ai.prereview.AiPrereviewStatusService;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.task.service.PagedResult;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SubmissionDtoMapper {

    private final AiPrereviewStatusService prereviewStatusService;

    public SubmissionDtoMapper(AiPrereviewStatusService prereviewStatusService) {
        this.prereviewStatusService = prereviewStatusService;
    }

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
        applyPrereview(dto, prereviewStatusService.viewFor(entity.getId()));
        return dto;
    }

    public PagedOwnerSubmissions toPagedOwnerSubmissions(PagedResult<SubmissionEntity> result) {
        PagedOwnerSubmissions dto = new PagedOwnerSubmissions();
        Map<Long, AiPrereviewSignalsView> prereviewBySubmissionId =
            prereviewStatusService.viewsFor(result.items().stream().map(SubmissionEntity::getId).toList());
        dto.setItems(result.items().stream()
            .map(entity -> toOwnerSubmissionSummary(entity, prereviewBySubmissionId))
            .toList());
        dto.setTotal(result.total());
        dto.setPage((int) result.page());
        dto.setSize((int) result.size());
        return dto;
    }

    private OwnerSubmissionSummary toOwnerSubmissionSummary(
        SubmissionEntity entity,
        Map<Long, AiPrereviewSignalsView> prereviewBySubmissionId
    ) {
        OwnerSubmissionSummary dto = new OwnerSubmissionSummary();
        dto.setId(entity.getId());
        dto.setSessionId(entity.getSessionId());
        dto.setTaskId(entity.getTaskId());
        dto.setDatasetItemId(entity.getDatasetItemId());
        dto.setSchemaVersionId(entity.getSchemaVersionId());
        dto.setLabelerId(entity.getLabelerId());
        dto.setStatus(entity.getStatusCode());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        applyPrereview(dto, prereviewBySubmissionId.getOrDefault(
            entity.getId(),
            prereviewStatusService.defaultView(entity.getId())
        ));
        return dto;
    }

    private void applyPrereview(Submission dto, AiPrereviewSignalsView view) {
        dto.setPrereviewStatus(PrereviewStatus.fromValue(view.status()));
        dto.setPrereviewSignals(toPrereviewSignals(view));
    }

    private void applyPrereview(OwnerSubmissionSummary dto, AiPrereviewSignalsView view) {
        dto.setPrereviewStatus(PrereviewStatus.fromValue(view.status()));
        dto.setPrereviewSignals(toPrereviewSignals(view));
    }

    private PrereviewSignals toPrereviewSignals(AiPrereviewSignalsView view) {
        PrereviewSignals dto = new PrereviewSignals();
        dto.setOutboxStatus(view.signals().outboxStatus());
        dto.setAiCallStatus(view.signals().aiCallStatus());
        dto.setHasAiOverallRecommendation(view.signals().hasAiOverallRecommendation());
        dto.setLastError(view.signals().lastError());
        return dto;
    }
}
