package com.labelhub.api.module.platform.cost;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformCostMetricsService {

    private static final BigDecimal ZERO_DECIMAL = BigDecimal.ZERO;

    private final PlatformCostMetricsMapper mapper;

    public PlatformCostMetricsService(PlatformCostMetricsMapper mapper) {
        this.mapper = mapper;
    }

    public PlatformCostMetrics getMetrics(OffsetDateTime from, OffsetDateTime to) {
        var fromLocal = from == null ? null : from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        var toLocal = to == null ? null : to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        PlatformCostAggregateRow overview = rowOrEmpty(mapper.selectOverview(fromLocal, toLocal));
        long callCount = longValue(overview.getCallCount());
        long totalTokens = longValue(overview.getTotalTokens());
        BigDecimal totalCost = decimalValue(overview.getTotalCost());
        long attributedCallCount = longValue(overview.getAttributedCallCount());
        long attributedTokens = longValue(overview.getAttributedTokens());
        BigDecimal attributedCost = decimalValue(overview.getAttributedCost());
        PlatformCostOverview facts = new PlatformCostOverview(
            callCount,
            totalTokens,
            totalCost,
            attributedCallCount,
            attributedTokens,
            attributedCost,
            Math.max(0L, callCount - attributedCallCount),
            Math.max(0L, totalTokens - attributedTokens),
            totalCost.subtract(attributedCost).max(ZERO_DECIMAL)
        );

        return new PlatformCostMetrics(
            OffsetDateTime.now(ZoneOffset.UTC),
            from,
            to,
            facts,
            toBuckets(mapper.selectDailyTrend(fromLocal, toLocal)),
            toBuckets(mapper.selectModelBreakdown(fromLocal, toLocal)),
            toBuckets(mapper.selectTaskBreakdown(fromLocal, toLocal)),
            toBuckets(mapper.selectOwnerBreakdown(fromLocal, toLocal))
        );
    }

    private static List<PlatformCostBucket> toBuckets(List<PlatformCostAggregateRow> rows) {
        return rows.stream()
            .map(row -> new PlatformCostBucket(
                row.getBucketDate(),
                row.getModelProvider(),
                row.getModelName(),
                row.getGroupId(),
                row.getGroupName(),
                longValue(row.getCallCount()),
                longValue(row.getTotalTokens()),
                decimalValue(row.getTotalCost())
            ))
            .toList();
    }

    private static PlatformCostAggregateRow rowOrEmpty(PlatformCostAggregateRow row) {
        return row == null ? new PlatformCostAggregateRow(null, null, null, null, null, "0", "0", "0", "0", "0", "0") : row;
    }

    private static long longValue(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal decimalValue(BigDecimal value) {
        return value == null ? ZERO_DECIMAL : value;
    }
}
