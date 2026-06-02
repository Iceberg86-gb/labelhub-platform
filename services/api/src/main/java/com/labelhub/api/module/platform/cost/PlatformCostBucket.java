package com.labelhub.api.module.platform.cost;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlatformCostBucket(
    LocalDate date,
    String modelProvider,
    String modelName,
    Long groupId,
    String groupName,
    long callCount,
    long totalTokens,
    BigDecimal totalCost
) {
}
