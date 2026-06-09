package com.labelhub.api.module.quality.web;

import com.labelhub.api.generated.model.BatchReviewItemResult;
import com.labelhub.api.generated.model.BatchReviewRequest;
import com.labelhub.api.generated.model.BatchReviewResult;
import com.labelhub.api.generated.model.CreateLedgerEntryRequest;
import com.labelhub.api.generated.model.MarkReviewDifficultyRequest;
import com.labelhub.api.generated.model.PagedQualityLedgerEntries;
import com.labelhub.api.generated.model.PagedReviewerSubmissions;
import com.labelhub.api.generated.model.PagedSeniorReviewCases;
import com.labelhub.api.generated.model.QualityLedgerEntry;
import com.labelhub.api.generated.model.RecomputeJob;
import com.labelhub.api.generated.model.RecomputeRuleRequest;
import com.labelhub.api.generated.model.ReviewLevel;
import com.labelhub.api.generated.model.ResolveSeniorReviewCaseRequest;
import com.labelhub.api.generated.model.SeniorReviewCase;
import com.labelhub.api.generated.model.Verdict;
import com.labelhub.api.generated.web.ReviewsApi;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.quality.entity.SeniorReviewCaseEntity;
import com.labelhub.api.module.quality.mapper.ReviewerSubmissionQueueRow;
import com.labelhub.api.module.quality.service.LedgerService;
import com.labelhub.api.module.quality.service.ReviewerBatchService;
import com.labelhub.api.module.quality.service.ReviewerQueueService;
import com.labelhub.api.module.quality.service.SeniorReviewCaseService;
import com.labelhub.api.module.quality.service.VerdictService;
import com.labelhub.api.module.quality.service.view.VerdictView;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ReviewerController implements ReviewsApi {

    private final LedgerService ledgerService;
    private final VerdictService verdictService;
    private final ReviewerQueueService reviewerQueueService;
    private final ReviewerBatchService reviewerBatchService;
    private final SeniorReviewCaseService seniorReviewCaseService;
    private final QualityDtoMapper qualityDtoMapper;

    public ReviewerController(
        LedgerService ledgerService,
        VerdictService verdictService,
        ReviewerQueueService reviewerQueueService,
        ReviewerBatchService reviewerBatchService,
        SeniorReviewCaseService seniorReviewCaseService,
        QualityDtoMapper qualityDtoMapper
    ) {
        this.ledgerService = ledgerService;
        this.verdictService = verdictService;
        this.reviewerQueueService = reviewerQueueService;
        this.reviewerBatchService = reviewerBatchService;
        this.seniorReviewCaseService = seniorReviewCaseService;
        this.qualityDtoMapper = qualityDtoMapper;
    }

    @Override
    public ResponseEntity<PagedReviewerSubmissions> listReviewerQueue(
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
        @Valid @RequestParam(value = "status", required = false, defaultValue = "submitted") String status,
        @Valid @RequestParam(value = "verdict", required = false) String verdict,
        @Valid @RequestParam(value = "reviewLevel", required = false, defaultValue = "reviewer") ReviewLevel reviewLevel
    ) {
        PagedResult<ReviewerSubmissionQueueRow> result = reviewerQueueService.listQueue(
            clampMin(page, 1),
            clampSize(size, 20, 100),
            status,
            verdict,
            reviewLevel == null ? null : reviewLevel.getValue()
        );
        return ResponseEntity.ok(qualityDtoMapper.toPagedReviewerSubmissions(result));
    }

    @Override
    public ResponseEntity<PagedSeniorReviewCases> listSeniorReviewCases(
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        PagedResult<SeniorReviewCaseEntity> result = seniorReviewCaseService.listOpenCases(
            clampMin(page, 1),
            clampSize(size, 20, 100)
        );
        return ResponseEntity.ok(qualityDtoMapper.toPagedSeniorReviewCases(result));
    }

    @Override
    public ResponseEntity<SeniorReviewCase> resolveSeniorReviewCase(
        @PathVariable("caseId") Long caseId,
        @Valid @RequestBody ResolveSeniorReviewCaseRequest resolveSeniorReviewCaseRequest
    ) {
        SeniorReviewCaseEntity entity = seniorReviewCaseService.resolveCase(
            caseId,
            currentUserId(),
            resolveSeniorReviewCaseRequest.getResolution().getValue(),
            resolveSeniorReviewCaseRequest.getReason(),
            resolveSeniorReviewCaseRequest.getAccountability()
        );
        return ResponseEntity.ok(qualityDtoMapper.toSeniorReviewCase(entity));
    }

    @Override
    public ResponseEntity<QualityLedgerEntry> createLedgerEntry(
        @PathVariable("submissionId") Long submissionId,
        @Valid @RequestBody CreateLedgerEntryRequest createLedgerEntryRequest
    ) {
        QualityLedgerEntryEntity entity = ledgerService.createEntry(
            submissionId,
            currentUserId(),
            createLedgerEntryRequest.getEntryType().getValue(),
            qualityDtoMapper.toPayloadMap(createLedgerEntryRequest.getPayload()),
            currentUserRoles()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(qualityDtoMapper.toQualityLedgerEntry(entity));
    }

    @Override
    public ResponseEntity<SeniorReviewCase> markSubmissionReviewDifficulty(
        @PathVariable("submissionId") Long submissionId,
        @Valid @RequestBody MarkReviewDifficultyRequest markReviewDifficultyRequest
    ) {
        SeniorReviewCaseEntity entity = seniorReviewCaseService.markReviewerDifficulty(
            submissionId,
            currentUserId(),
            markReviewDifficultyRequest.getReason()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(qualityDtoMapper.toSeniorReviewCase(entity));
    }

    @Override
    public ResponseEntity<BatchReviewResult> batchReviewSubmissions(
        @Valid @RequestBody BatchReviewRequest request
    ) {
        com.labelhub.api.module.quality.service.ReviewerBatchResult result =
            reviewerBatchService.reviewSubmissions(
                request.getSubmissionIds(),
                currentUserId(),
                request.getVerdict().getValue(),
                request.getReason(),
                request.getReviewLevel().getValue(),
                currentUserRoles()
            );
        BatchReviewResult dto = new BatchReviewResult();
        dto.setItems(result.items().stream().map(item -> {
            BatchReviewItemResult itemDto = new BatchReviewItemResult();
            itemDto.setSubmissionId(item.submissionId());
            itemDto.setStatus(BatchReviewItemResult.StatusEnum.fromValue(item.status()));
            itemDto.setLedgerEntryId(item.ledgerEntryId());
            itemDto.setError(item.error());
            return itemDto;
        }).toList());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<PagedQualityLedgerEntries> listSubmissionLedger(
        @PathVariable("submissionId") Long submissionId,
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(200) @Valid @RequestParam(value = "size", required = false, defaultValue = "50") Integer size
    ) {
        PagedResult<QualityLedgerEntryEntity> result = ledgerService.listEntries(
            submissionId,
            currentUserId(),
            currentUserRoles(),
            clampMin(page, 1),
            clampSize(size, 50, 200)
        );
        return ResponseEntity.ok(qualityDtoMapper.toPagedQualityLedgerEntries(result));
    }

    @Override
    public ResponseEntity<Verdict> getSubmissionVerdict(@PathVariable("submissionId") Long submissionId) {
        VerdictView view = verdictService.deriveCurrentVerdict(
            submissionId,
            currentUserId(),
            currentUserRoles()
        );
        return ResponseEntity.ok(qualityDtoMapper.toVerdict(view));
    }

    @Override
    public ResponseEntity<RecomputeJob> recomputeAdjudicationRule(
        @PathVariable("ruleId") Long ruleId,
        @Valid @RequestBody(required = false) RecomputeRuleRequest recomputeRuleRequest
    ) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Adjudication recompute is not implemented in M4");
    }

    private long clampMin(Integer value, long minimum) {
        if (value == null || value < minimum) {
            return minimum;
        }
        return value;
    }

    private long clampSize(Integer value, long defaultValue, long maximum) {
        long effective = value == null ? defaultValue : value;
        if (effective < 1) {
            return defaultValue;
        }
        return Math.min(effective, maximum);
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }

    private Set<String> currentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return Set.copyOf(jwtPrincipal.roles());
        }
        return Set.of();
    }
}
