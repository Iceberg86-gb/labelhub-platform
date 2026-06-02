package com.labelhub.api.module.platform.efficiency;

import java.time.OffsetDateTime;

public record PlatformEfficiencyMetrics(
    OffsetDateTime generatedAt,
    PlatformIdempotencyMetrics idempotency,
    PlatformUnitCostMetrics unitCost,
    boolean empty
) {
}
