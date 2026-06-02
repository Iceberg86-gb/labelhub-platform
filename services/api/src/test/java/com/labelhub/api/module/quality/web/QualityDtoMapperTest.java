package com.labelhub.api.module.quality.web;

import com.labelhub.api.generated.model.AiFieldFindingPayload;
import com.labelhub.api.generated.model.QualityLedgerEntryType;
import com.labelhub.api.generated.model.ReviewerOverallVerdictPayload;
import com.labelhub.api.generated.model.Verdict;
import com.labelhub.api.module.ai.prereview.AiPrereviewStatusMapper;
import com.labelhub.api.module.ai.prereview.AiPrereviewStatusService;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.ReviewerSubmissionQueueRow;
import com.labelhub.api.module.quality.service.view.VerdictView;
import com.labelhub.api.module.task.service.PagedResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityDtoMapperTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);
    private final AiPrereviewStatusMapper prereviewStatusMapper = mock(AiPrereviewStatusMapper.class);
    private final AiPrereviewStatusService prereviewStatusService = new AiPrereviewStatusService(
        prereviewStatusMapper,
        fixedClock
    );
    private final QualityDtoMapper mapper = new QualityDtoMapper(
        fixedClock,
        prereviewStatusService
    );

    @Test
    void toQualityLedgerEntry_maps_physical_columns_to_public_contract_names() {
        var dto = mapper.toQualityLedgerEntry(ledgerEntry());

        assertThat(dto.getEntryType()).isEqualTo(QualityLedgerEntryType.REVIEWER_OVERALL_VERDICT);
        assertThat(dto.getActorUserId()).isEqualTo(3003L);
        assertThat(dto.getPayload()).isInstanceOf(ReviewerOverallVerdictPayload.class);
        ReviewerOverallVerdictPayload payload = (ReviewerOverallVerdictPayload) dto.getPayload();
        assertThat(payload.getVerdict()).isEqualTo(ReviewerOverallVerdictPayload.VerdictEnum.APPROVE);
        assertThat(payload.getReason()).isEqualTo("Looks consistent");
        assertThat(dto.getCreatedAt()).isEqualTo(OffsetDateTime.of(2026, 5, 25, 11, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toQualityLedgerEntry_maps_ai_field_finding_payload_without_actor_user() {
        var dto = mapper.toQualityLedgerEntry(aiFieldFindingEntry());

        assertThat(dto.getEntryType()).isEqualTo(QualityLedgerEntryType.AI_FIELD_FINDING);
        assertThat(dto.getActorType()).isEqualTo("ai");
        assertThat(dto.getActorUserId()).isNull();
        assertThat(dto.getAiCallId()).isEqualTo(700L);
        assertThat(dto.getPayload()).isInstanceOf(AiFieldFindingPayload.class);
        AiFieldFindingPayload payload = (AiFieldFindingPayload) dto.getPayload();
        assertThat(payload.getFieldPath()).isEqualTo("answer.name");
        assertThat(payload.getStableId()).isEqualTo("name");
        assertThat(payload.getLabel()).isEqualTo("Name");
        assertThat(payload.getSeverity()).isEqualTo(AiFieldFindingPayload.SeverityEnum.WARNING);
        assertThat(payload.getFinding()).isEqualTo("Looks suspicious");
        assertThat(payload.getConfidence()).isEqualTo(0.8f);
    }

    @Test
    void toPayloadMap_converts_strong_payload_dto_to_persisted_shape() {
        ReviewerOverallVerdictPayload payload = new ReviewerOverallVerdictPayload()
            .verdict(ReviewerOverallVerdictPayload.VerdictEnum.REJECT)
            .reason("Missing evidence");

        Map<String, Object> result = mapper.toPayloadMap(payload);

        assertThat(result).containsEntry("verdict", "reject")
            .containsEntry("reason", "Missing evidence");
    }

    @Test
    void toVerdict_maps_live_derived_view() {
        Verdict dto = mapper.toVerdict(new VerdictView(
            900L,
            "approved",
            123L,
            LocalDateTime.parse("2026-05-25T11:45:00")
        ));

        assertThat(dto.getSubmissionId()).isEqualTo(900L);
        assertThat(dto.getStatus()).isEqualTo(Verdict.StatusEnum.APPROVED);
        assertThat(dto.getDerivedFromEntryId()).isEqualTo(123L);
        assertThat(dto.getDerivedAt()).isEqualTo(OffsetDateTime.of(2026, 5, 25, 11, 45, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void toPagedReviewerSubmissions_derives_pending_verdict_from_queue_row_without_entry() {
        ReviewerSubmissionQueueRow row = queueRow();
        row.setReviewerVerdict(null);
        row.setDerivedFromEntryId(null);
        when(prereviewStatusMapper.selectSignalsBySubmissionIds(List.of(900L))).thenReturn(List.of());

        var result = mapper.toPagedReviewerSubmissions(new PagedResult<>(List.of(row), 1, 1, 20));

        assertThat(result.getItems()).singleElement()
            .satisfies(item -> {
                assertThat(item.getTaskTitle()).isEqualTo("Reviewable task");
                assertThat(item.getVerdict().getStatus()).isEqualTo(Verdict.StatusEnum.PENDING);
                assertThat(item.getVerdict().getDerivedFromEntryId()).isNull();
                assertThat(item.getVerdict().getDerivedAt()).isEqualTo(
                    OffsetDateTime.of(2026, 5, 25, 12, 0, 0, 0, ZoneOffset.UTC));
                assertThat(item.getPrereviewStatus().getValue()).isEqualTo("pending");
            });
    }

    private QualityLedgerEntryEntity ledgerEntry() {
        QualityLedgerEntryEntity entity = new QualityLedgerEntryEntity();
        entity.setId(100L);
        entity.setSubmissionId(900L);
        entity.setTaskId(80L);
        entity.setEvidenceType("reviewer_overall_verdict");
        entity.setActorType("reviewer");
        entity.setActorId(3003L);
        entity.setAiCallId(null);
        entity.setPayload(Map.of("verdict", "approve", "reason", "Looks consistent"));
        entity.setCreatedAt(LocalDateTime.parse("2026-05-25T11:30:00"));
        return entity;
    }

    private QualityLedgerEntryEntity aiFieldFindingEntry() {
        QualityLedgerEntryEntity entity = new QualityLedgerEntryEntity();
        entity.setId(101L);
        entity.setSubmissionId(900L);
        entity.setTaskId(80L);
        entity.setEvidenceType("ai_field_finding");
        entity.setActorType("ai");
        entity.setActorId(null);
        entity.setAiCallId(700L);
        entity.setPayload(Map.of(
            "fieldPath", "answer.name",
            "stableId", "name",
            "label", "Name",
            "severity", "warning",
            "finding", "Looks suspicious",
            "confidence", 0.8
        ));
        entity.setCreatedAt(LocalDateTime.parse("2026-05-25T11:35:00"));
        return entity;
    }

    private ReviewerSubmissionQueueRow queueRow() {
        ReviewerSubmissionQueueRow row = new ReviewerSubmissionQueueRow();
        row.setId(900L);
        row.setTaskId(80L);
        row.setTaskTitle("Reviewable task");
        row.setLabelerId(2002L);
        row.setSchemaVersionId(70L);
        row.setStatusCode("submitted");
        row.setSubmittedAt(LocalDateTime.parse("2026-05-25T11:00:00"));
        return row;
    }
}
