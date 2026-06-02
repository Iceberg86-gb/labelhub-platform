package com.labelhub.api.module.platform.efficiency;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class PlatformEfficiencyMetricsService {

    private final PlatformEfficiencyMetricsMapper mapper;

    public PlatformEfficiencyMetricsService(PlatformEfficiencyMetricsMapper mapper) {
        this.mapper = mapper;
    }

    public PlatformEfficiencyMetrics getMetrics() {
        PlatformIdempotencyMetrics idempotency = mapper.selectIdempotencyMetrics();
        PlatformUnitCostMetrics unitCost = mapper.selectUnitCostMetrics();
        boolean empty = idempotency.callCount() == 0 && unitCost.distinctSubmissionCount() == 0;
        return new PlatformEfficiencyMetrics(OffsetDateTime.now(ZoneOffset.UTC), idempotency, unitCost, empty);
    }
}
