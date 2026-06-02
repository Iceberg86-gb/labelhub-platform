package com.labelhub.api.module.platform.web;

import com.labelhub.api.module.platform.cost.PlatformCostBucket;
import com.labelhub.api.module.platform.cost.PlatformCostMetricsService;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/cost-metrics")
public class PlatformCostMetricsController {

    private final PlatformCostMetricsService service;

    public PlatformCostMetricsController(PlatformCostMetricsService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping(produces = "application/json")
    public ResponseEntity<com.labelhub.api.generated.model.PlatformCostMetrics> getPlatformCostMetrics(
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime to
    ) {
        return ResponseEntity.ok(toResponse(service.getMetrics(from, to)));
    }

    private com.labelhub.api.generated.model.PlatformCostMetrics toResponse(
        com.labelhub.api.module.platform.cost.PlatformCostMetrics metrics
    ) {
        com.labelhub.api.generated.model.PlatformCostMetrics response =
            new com.labelhub.api.generated.model.PlatformCostMetrics();
        response.setGeneratedAt(metrics.generatedAt());
        response.setFrom(metrics.from());
        response.setTo(metrics.to());
        response.setOverview(toOverview(metrics.overview()));
        response.setDailyTrend(metrics.dailyTrend().stream().map(this::toBucket).toList());
        response.setModelBreakdown(metrics.modelBreakdown().stream().map(this::toBucket).toList());
        response.setTaskBreakdown(metrics.taskBreakdown().stream().map(this::toBucket).toList());
        response.setOwnerBreakdown(metrics.ownerBreakdown().stream().map(this::toBucket).toList());
        response.setEmpty(metrics.isEmpty());
        return response;
    }

    private com.labelhub.api.generated.model.PlatformCostOverview toOverview(
        com.labelhub.api.module.platform.cost.PlatformCostOverview overview
    ) {
        com.labelhub.api.generated.model.PlatformCostOverview response =
            new com.labelhub.api.generated.model.PlatformCostOverview();
        response.setCallCount(overview.callCount());
        response.setTotalTokens(overview.totalTokens());
        response.setTotalCost(overview.totalCost());
        response.setAttributedCallCount(overview.attributedCallCount());
        response.setAttributedTokens(overview.attributedTokens());
        response.setAttributedCost(overview.attributedCost());
        response.setUnattributedCallCount(overview.unattributedCallCount());
        response.setUnattributedTokens(overview.unattributedTokens());
        response.setUnattributedCost(overview.unattributedCost());
        return response;
    }

    private com.labelhub.api.generated.model.PlatformCostBucket toBucket(PlatformCostBucket bucket) {
        com.labelhub.api.generated.model.PlatformCostBucket response =
            new com.labelhub.api.generated.model.PlatformCostBucket();
        response.setDate(bucket.date());
        response.setModelProvider(bucket.modelProvider());
        response.setModelName(bucket.modelName());
        response.setGroupId(bucket.groupId());
        response.setGroupName(bucket.groupName());
        response.setCallCount(bucket.callCount());
        response.setTotalTokens(bucket.totalTokens());
        response.setTotalCost(bucket.totalCost());
        return response;
    }
}
