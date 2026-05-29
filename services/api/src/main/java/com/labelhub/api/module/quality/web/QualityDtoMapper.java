package com.labelhub.api.module.quality.web;

import com.labelhub.api.generated.model.AiFieldFindingPayload;
import com.labelhub.api.generated.model.AiOverallRecommendationPayload;
import com.labelhub.api.generated.model.DimensionScore;
import com.labelhub.api.generated.model.PagedQualityLedgerEntries;
import com.labelhub.api.generated.model.PagedReviewerSubmissions;
import com.labelhub.api.generated.model.QualityLedgerEntry;
import com.labelhub.api.generated.model.QualityLedgerEntryPayload;
import com.labelhub.api.generated.model.QualityLedgerEntryType;
import com.labelhub.api.generated.model.ReviewerOverallVerdictPayload;
import com.labelhub.api.generated.model.ReviewerSubmissionSummary;
import com.labelhub.api.generated.model.Verdict;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.mapper.ReviewerSubmissionQueueRow;
import com.labelhub.api.module.quality.service.view.VerdictView;
import java.math.BigDecimal;
import com.labelhub.api.module.task.service.PagedResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QualityDtoMapper {

    private final Clock clock;

    public QualityDtoMapper(Clock clock) {
        this.clock = clock;
    }

    public QualityLedgerEntry toQualityLedgerEntry(QualityLedgerEntryEntity entity) {
        QualityLedgerEntry dto = new QualityLedgerEntry();
        dto.setId(entity.getId());
        dto.setSubmissionId(entity.getSubmissionId());
        dto.setTaskId(entity.getTaskId());
        dto.setEntryType(QualityLedgerEntryType.fromValue(entity.getEvidenceType()));
        dto.setActorType(entity.getActorType());
        dto.setActorUserId(entity.getActorId());
        dto.setAiCallId(entity.getAiCallId());
        dto.setPayload(toPayloadDto(entity.getEvidenceType(), entity.getPayload()));
        dto.setCreatedAt(offset(entity.getCreatedAt()));
        return dto;
    }

    public PagedQualityLedgerEntries toPagedQualityLedgerEntries(PagedResult<QualityLedgerEntryEntity> result) {
        PagedQualityLedgerEntries dto = new PagedQualityLedgerEntries();
        dto.setItems(result.items().stream().map(this::toQualityLedgerEntry).toList());
        dto.setTotal(result.total());
        dto.setPage(Math.toIntExact(result.page()));
        dto.setSize(Math.toIntExact(result.size()));
        return dto;
    }

    public QualityLedgerEntryPayload toPayloadDto(String entryType, Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        if ("ai_field_finding".equals(entryType)) {
            return toAiFieldFindingPayload(raw);
        }
        if ("ai_overall_recommendation".equals(entryType)) {
            return toAiOverallRecommendationPayload(raw);
        }
        return toReviewerOverallVerdictPayload(raw);
    }

    private ReviewerOverallVerdictPayload toReviewerOverallVerdictPayload(Map<String, Object> raw) {
        ReviewerOverallVerdictPayload dto = new ReviewerOverallVerdictPayload();
        Object verdict = raw.get("verdict");
        if (verdict != null) {
            dto.setVerdict(ReviewerOverallVerdictPayload.VerdictEnum.fromValue(String.valueOf(verdict)));
        }
        Object reason = raw.get("reason");
        if (reason != null) {
            dto.setReason(String.valueOf(reason));
        }
        return dto;
    }

    private AiOverallRecommendationPayload toAiOverallRecommendationPayload(Map<String, Object> raw) {
        AiOverallRecommendationPayload dto = new AiOverallRecommendationPayload();
        Object recommendation = raw.get("recommendation");
        if (recommendation != null) {
            dto.setRecommendation(AiOverallRecommendationPayload.RecommendationEnum.fromValue(String.valueOf(recommendation)));
        }
        Object finalScore = raw.get("finalScore");
        if (finalScore != null) {
            dto.setFinalScore(decimalValue(finalScore));
        }
        Object threshold = raw.get("threshold");
        if (threshold != null) {
            dto.setThreshold(decimalValue(threshold));
        }
        Object rejectFloor = raw.get("rejectFloor");
        if (rejectFloor != null) {
            dto.setRejectFloor(decimalValue(rejectFloor));
        }
        Object scoringRuleVersion = raw.get("scoringRuleVersion");
        if (scoringRuleVersion != null) {
            dto.setScoringRuleVersion(String.valueOf(scoringRuleVersion));
        }
        Object summary = raw.get("summary");
        if (summary != null) {
            dto.setSummary(String.valueOf(summary));
        }
        if (raw.get("dimensionScores") instanceof java.util.List<?> rows) {
            dto.setDimensionScores(rows.stream()
                .filter(java.util.Map.class::isInstance)
                .map(java.util.Map.class::cast)
                .map(this::toDimensionScore)
                .toList());
        }
        return dto;
    }

    private DimensionScore toDimensionScore(Map<?, ?> raw) {
        DimensionScore dto = new DimensionScore();
        Object dimension = raw.get("dimension");
        if (dimension != null) {
            dto.setDimension(String.valueOf(dimension));
        }
        Object score = raw.get("score");
        if (score != null) {
            dto.setScore(decimalValue(score));
        }
        Object reason = raw.get("reason");
        if (reason != null) {
            dto.setReason(String.valueOf(reason));
        }
        return dto;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private AiFieldFindingPayload toAiFieldFindingPayload(Map<String, Object> raw) {
        AiFieldFindingPayload dto = new AiFieldFindingPayload();
        Object fieldPath = raw.get("fieldPath");
        if (fieldPath != null) {
            dto.setFieldPath(String.valueOf(fieldPath));
        }
        Object stableId = raw.get("stableId");
        if (stableId != null) {
            dto.setStableId(String.valueOf(stableId));
        }
        Object label = raw.get("label");
        if (label != null) {
            dto.setLabel(String.valueOf(label));
        }
        Object severity = raw.get("severity");
        if (severity != null) {
            dto.setSeverity(AiFieldFindingPayload.SeverityEnum.fromValue(String.valueOf(severity)));
        }
        Object finding = raw.get("finding");
        if (finding != null) {
            dto.setFinding(String.valueOf(finding));
        }
        Object confidence = raw.get("confidence");
        if (confidence instanceof Number number) {
            dto.setConfidence(number.floatValue());
        } else if (confidence != null) {
            dto.setConfidence(Float.valueOf(String.valueOf(confidence)));
        }
        return dto;
    }

    public Map<String, Object> toPayloadMap(ReviewerOverallVerdictPayload dto) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (dto == null) {
            return payload;
        }
        if (dto.getVerdict() != null) {
            payload.put("verdict", dto.getVerdict().getValue());
        }
        if (dto.getReason() != null) {
            payload.put("reason", dto.getReason());
        }
        return payload;
    }

    public Verdict toVerdict(VerdictView view) {
        Verdict dto = new Verdict();
        dto.setSubmissionId(view.submissionId());
        dto.setStatus(Verdict.StatusEnum.fromValue(view.status()));
        dto.setDerivedFromEntryId(view.derivedFromEntryId());
        dto.setDerivedAt(offset(view.derivedAt()));
        return dto;
    }

    public PagedReviewerSubmissions toPagedReviewerSubmissions(PagedResult<ReviewerSubmissionQueueRow> result) {
        PagedReviewerSubmissions dto = new PagedReviewerSubmissions();
        dto.setItems(result.items().stream().map(this::toReviewerSubmissionSummary).toList());
        dto.setTotal(result.total());
        dto.setPage(Math.toIntExact(result.page()));
        dto.setSize(Math.toIntExact(result.size()));
        return dto;
    }

    public ReviewerSubmissionSummary toReviewerSubmissionSummary(ReviewerSubmissionQueueRow row) {
        ReviewerSubmissionSummary dto = new ReviewerSubmissionSummary();
        dto.setId(row.getId());
        dto.setTaskId(row.getTaskId());
        dto.setTaskTitle(row.getTaskTitle());
        dto.setLabelerId(row.getLabelerId());
        dto.setSchemaVersionId(row.getSchemaVersionId());
        dto.setStatus(row.getStatusCode());
        dto.setSubmittedAt(offset(row.getSubmittedAt()));
        dto.setVerdict(toVerdict(new VerdictView(
            row.getId(),
            statusFromReviewerVerdict(row.getReviewerVerdict()),
            row.getDerivedFromEntryId(),
            LocalDateTime.now(clock)
        )));
        if (row.getAiRecommendation() != null) {
            dto.setAiRecommendation(ReviewerSubmissionSummary.AiRecommendationEnum.fromValue(row.getAiRecommendation()));
        }
        return dto;
    }

    private String statusFromReviewerVerdict(String reviewerVerdict) {
        if ("approve".equals(reviewerVerdict)) {
            return "approved";
        }
        if ("reject".equals(reviewerVerdict)) {
            return "rejected";
        }
        return "pending";
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
