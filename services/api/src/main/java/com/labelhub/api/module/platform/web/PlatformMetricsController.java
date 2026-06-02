package com.labelhub.api.module.platform.web;

import com.labelhub.api.module.platform.efficiency.PlatformEfficiencyMetricsService;
import com.labelhub.api.module.platform.labor.PlatformLaborMetricRow;
import com.labelhub.api.module.platform.labor.PlatformLaborMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform")
public class PlatformMetricsController {

    private final PlatformLaborMetricsService laborService;
    private final PlatformEfficiencyMetricsService efficiencyService;

    public PlatformMetricsController(
        PlatformLaborMetricsService laborService,
        PlatformEfficiencyMetricsService efficiencyService
    ) {
        this.laborService = laborService;
        this.efficiencyService = efficiencyService;
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping(value = "/labor-metrics", produces = "application/json")
    public ResponseEntity<com.labelhub.api.generated.model.PlatformLaborMetrics> getLaborMetrics() {
        return ResponseEntity.ok(toLaborResponse(laborService.getMetrics()));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping(value = "/efficiency-metrics", produces = "application/json")
    public ResponseEntity<com.labelhub.api.generated.model.PlatformEfficiencyMetrics> getEfficiencyMetrics() {
        return ResponseEntity.ok(toEfficiencyResponse(efficiencyService.getMetrics()));
    }

    private com.labelhub.api.generated.model.PlatformLaborMetrics toLaborResponse(
        com.labelhub.api.module.platform.labor.PlatformLaborMetrics metrics
    ) {
        com.labelhub.api.generated.model.PlatformLaborMetrics response =
            new com.labelhub.api.generated.model.PlatformLaborMetrics();
        response.setGeneratedAt(metrics.generatedAt());
        response.setSubmissions(metrics.submissions().stream().map(this::toLaborRow).toList());
        response.setReviews(metrics.reviews().stream().map(this::toLaborRow).toList());
        com.labelhub.api.generated.model.PlatformReworkMetrics rework =
            new com.labelhub.api.generated.model.PlatformReworkMetrics();
        rework.setSupersededSubmissionCount(metrics.rework().supersededSubmissionCount());
        rework.setMultiRoundReviewActionCount(metrics.rework().multiRoundReviewActionCount());
        rework.setReturnedForRevisionSubmissionCount(metrics.rework().returnedForRevisionSubmissionCount());
        response.setRework(rework);
        response.setEmpty(metrics.empty());
        return response;
    }

    private com.labelhub.api.generated.model.PlatformLaborMetricRow toLaborRow(PlatformLaborMetricRow row) {
        com.labelhub.api.generated.model.PlatformLaborMetricRow response =
            new com.labelhub.api.generated.model.PlatformLaborMetricRow();
        response.setUserId(row.userId());
        response.setDisplayName(row.displayName());
        response.setUsername(row.username());
        response.setCount(row.count());
        response.setInitialReviewCount(row.initialReviewCount());
        response.setSeniorReviewCount(row.seniorReviewCount());
        response.setApproveActionCount(row.approveActionCount());
        response.setReturnActionCount(row.returnActionCount());
        response.setRejectActionCount(row.rejectActionCount());
        return response;
    }

    private com.labelhub.api.generated.model.PlatformEfficiencyMetrics toEfficiencyResponse(
        com.labelhub.api.module.platform.efficiency.PlatformEfficiencyMetrics metrics
    ) {
        com.labelhub.api.generated.model.PlatformEfficiencyMetrics response =
            new com.labelhub.api.generated.model.PlatformEfficiencyMetrics();
        response.setGeneratedAt(metrics.generatedAt());
        com.labelhub.api.generated.model.PlatformIdempotencyMetrics idempotency =
            new com.labelhub.api.generated.model.PlatformIdempotencyMetrics();
        idempotency.setCallCount(metrics.idempotency().callCount());
        idempotency.setUniqueKeyCount(metrics.idempotency().uniqueKeyCount());
        idempotency.setDuplicateKeyCount(metrics.idempotency().duplicateKeyCount());
        idempotency.setCacheHitTokens(metrics.idempotency().cacheHitTokens());
        response.setIdempotency(idempotency);

        com.labelhub.api.generated.model.PlatformUnitCostMetrics unitCost =
            new com.labelhub.api.generated.model.PlatformUnitCostMetrics();
        unitCost.setTotalCost(metrics.unitCost().totalCost());
        unitCost.setDistinctSubmissionCount(metrics.unitCost().distinctSubmissionCount());
        unitCost.setDistinctDatasetItemCount(metrics.unitCost().distinctDatasetItemCount());
        unitCost.setCostPerSubmission(metrics.unitCost().costPerSubmission());
        unitCost.setCostPerDatasetItem(metrics.unitCost().costPerDatasetItem());
        response.setUnitCost(unitCost);
        response.setEmpty(metrics.empty());
        return response;
    }
}
