package com.labelhub.api.module.quality.web;

import com.labelhub.api.generated.model.AiFieldFindingPayload;
import com.labelhub.api.generated.model.AiOverallRecommendationPayload;
import com.labelhub.api.generated.model.DimensionScore;
import com.labelhub.api.generated.model.PagedQualityLedgerEntries;
import com.labelhub.api.generated.model.PagedReviewerSubmissions;
import com.labelhub.api.generated.model.PagedSeniorReviewCases;
import com.labelhub.api.generated.model.PrereviewSignals;
import com.labelhub.api.generated.model.PrereviewStatus;
import com.labelhub.api.generated.model.QualityLedgerEntry;
import com.labelhub.api.generated.model.QualityLedgerEntryPayload;
import com.labelhub.api.generated.model.QualityLedgerEntryType;
import com.labelhub.api.generated.model.ReviewLevel;
import com.labelhub.api.generated.model.ReviewerOverallVerdictPayload;
import com.labelhub.api.generated.model.ReviewerSubmissionSummary;
import com.labelhub.api.generated.model.SeniorReviewCase;
import com.labelhub.api.generated.model.SeniorReviewCaseResolution;
import com.labelhub.api.generated.model.SeniorReviewCaseSourceSignal;
import com.labelhub.api.generated.model.SeniorReviewCaseStatus;
import com.labelhub.api.generated.model.SeniorReviewCaseType;
import com.labelhub.api.generated.model.Verdict;
import com.labelhub.api.module.ai.prereview.AiPrereviewSignalsView;
import com.labelhub.api.module.ai.prereview.AiPrereviewStatusService;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.entity.SeniorReviewCaseEntity;
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
    private final AiPrereviewStatusService prereviewStatusService;

    public QualityDtoMapper(Clock clock, AiPrereviewStatusService prereviewStatusService) {
        this.clock = clock;
        this.prereviewStatusService = prereviewStatusService;
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
        Object reviewLevel = raw.get("reviewLevel");
        if (reviewLevel != null) {
            dto.setReviewLevel(ReviewLevel.fromValue(String.valueOf(reviewLevel)));
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
        if (dto.getReviewLevel() != null) {
            payload.put("reviewLevel", dto.getReviewLevel().getValue());
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
        Map<Long, AiPrereviewSignalsView> prereviewBySubmissionId =
            prereviewStatusService.viewsFor(result.items().stream().map(ReviewerSubmissionQueueRow::getId).toList());
        dto.setItems(result.items().stream()
            .map(row -> toReviewerSubmissionSummary(row, prereviewBySubmissionId))
            .toList());
        dto.setTotal(result.total());
        dto.setPage(Math.toIntExact(result.page()));
        dto.setSize(Math.toIntExact(result.size()));
        return dto;
    }

    public PagedSeniorReviewCases toPagedSeniorReviewCases(PagedResult<SeniorReviewCaseEntity> result) {
        PagedSeniorReviewCases dto = new PagedSeniorReviewCases();
        dto.setItems(result.items().stream().map(this::toSeniorReviewCase).toList());
        dto.setTotal(result.total());
        dto.setPage(Math.toIntExact(result.page()));
        dto.setSize(Math.toIntExact(result.size()));
        return dto;
    }

    public SeniorReviewCase toSeniorReviewCase(SeniorReviewCaseEntity entity) {
        SeniorReviewCase dto = new SeniorReviewCase();
        dto.setId(entity.getId());
        dto.setSubmissionId(entity.getSubmissionId());
        dto.setTaskId(entity.getTaskId());
        dto.setTaskTitle(entity.getTaskTitle());
        dto.setSchemaName(entity.getSchemaName());
        dto.setSchemaVersionNumber(entity.getSchemaVersionNumber());
        dto.setCaseType(SeniorReviewCaseType.fromValue(entity.getCaseType()));
        dto.setSourceSignal(SeniorReviewCaseSourceSignal.fromValue(entity.getSourceSignal()));
        dto.setSourceSummary(sourceSummary(entity));
        dto.setStatus(SeniorReviewCaseStatus.fromValue(entity.getStatus()));
        dto.setPriority(SeniorReviewCase.PriorityEnum.fromValue(entity.getPriority()));
        dto.setReviewerVerdictEntryId(entity.getReviewerVerdictEntryId());
        dto.setAiOverallEntryId(entity.getAiOverallEntryId());
        dto.setReviewerId(entity.getReviewerId());
        dto.setSeniorReviewerId(entity.getSeniorReviewerId());
        if (entity.getResolution() != null) {
            dto.setResolution(SeniorReviewCaseResolution.fromValue(entity.getResolution()));
        }
        dto.setReason(entity.getReason());
        dto.setPayload(entity.getPayload());
        dto.setAccountability(entity.getAccountability());
        dto.setCreatedAt(offset(entity.getCreatedAt()));
        dto.setUpdatedAt(offset(entity.getUpdatedAt()));
        dto.setResolvedAt(offset(entity.getResolvedAt()));
        return dto;
    }

    public ReviewerSubmissionSummary toReviewerSubmissionSummary(
        ReviewerSubmissionQueueRow row,
        Map<Long, AiPrereviewSignalsView> prereviewBySubmissionId
    ) {
        ReviewerSubmissionSummary dto = new ReviewerSubmissionSummary();
        dto.setId(row.getId());
        dto.setTaskId(row.getTaskId());
        dto.setTaskTitle(row.getTaskTitle());
        dto.setLabelerId(row.getLabelerId());
        dto.setSchemaVersionId(row.getSchemaVersionId());
        dto.setSchemaName(row.getSchemaName());
        dto.setSchemaVersionNumber(row.getSchemaVersionNumber());
        dto.setStatus(row.getStatusCode());
        dto.setSubmittedAt(offset(row.getSubmittedAt()));
        dto.setVerdict(toVerdict(new VerdictView(
            row.getId(),
            statusFromReviewerVerdict(row.getReviewerVerdict()),
            row.getDerivedFromEntryId(),
            LocalDateTime.now(clock)
        )));
        dto.setReviewLevel(ReviewLevel.fromValue(reviewLevelOrDefault(row.getReviewLevel())));
        if (row.getAiRecommendation() != null) {
            dto.setAiRecommendation(ReviewerSubmissionSummary.AiRecommendationEnum.fromValue(row.getAiRecommendation()));
        }
        applyPrereview(dto, prereviewBySubmissionId.getOrDefault(
            row.getId(),
            prereviewStatusService.defaultView(row.getId())
        ));
        return dto;
    }

    private void applyPrereview(ReviewerSubmissionSummary dto, AiPrereviewSignalsView view) {
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

    private String statusFromReviewerVerdict(String reviewerVerdict) {
        if ("approve".equals(reviewerVerdict)) {
            return "approved";
        }
        if ("reject".equals(reviewerVerdict)) {
            return "rejected";
        }
        return "pending";
    }

    private String reviewLevelOrDefault(String reviewLevel) {
        return reviewLevel == null || reviewLevel.isBlank() ? "reviewer" : reviewLevel;
    }

    private String sourceSummary(SeniorReviewCaseEntity entity) {
        return switch (entity.getSourceSignal()) {
            case "ai_manual_review" -> "AI 建议人工复核";
            case "ai_error_conflict" -> "AI 高置信错误与初审通过冲突";
            case "reviewer_difficulty" -> "初审主动标记疑难";
            case "sampling" -> "平静条目抽检";
            default -> entity.getSourceSignal();
        };
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
