package com.labelhub.api.module.platform.cost;

import java.math.BigDecimal;

public record PlatformCostOverview(
    long callCount,
    long totalTokens,
    BigDecimal totalCost,
    long attributedCallCount,
    long attributedTokens,
    BigDecimal attributedCost,
    long unattributedCallCount,
    long unattributedTokens,
    BigDecimal unattributedCost
) {
}
