package com.labelhub.api.module.platform.labor;

import java.time.OffsetDateTime;
import java.util.List;

public record PlatformLaborMetrics(
    OffsetDateTime generatedAt,
    List<PlatformLaborMetricRow> submissions,
    List<PlatformLaborMetricRow> reviews,
    PlatformReworkMetrics rework,
    boolean empty
) {
}
