package com.labelhub.api.module.platform.cost;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformCostMetrics(
    OffsetDateTime generatedAt,
    OffsetDateTime from,
    OffsetDateTime to,
    PlatformCostOverview overview,
    List<PlatformCostBucket> dailyTrend,
    List<PlatformCostBucket> modelBreakdown,
    List<PlatformCostBucket> taskBreakdown,
    List<PlatformCostBucket> ownerBreakdown
) {
    public boolean isEmpty() {
        return overview.callCount() == 0
            && dailyTrend.isEmpty()
            && modelBreakdown.isEmpty()
            && taskBreakdown.isEmpty()
            && ownerBreakdown.isEmpty();
    }
}
