package com.labelhub.api.module.platform.service;

import com.labelhub.api.module.platform.cost.PlatformCostAggregateRow;
import com.labelhub.api.module.platform.cost.PlatformCostMetrics;
import com.labelhub.api.module.platform.cost.PlatformCostMetricsMapper;
import com.labelhub.api.module.platform.cost.PlatformCostMetricsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformCostMetricsServiceTest {

    private final PlatformCostMetricsMapper mapper = mock(PlatformCostMetricsMapper.class);
    private final PlatformCostMetricsService service = new PlatformCostMetricsService(mapper);

    @Test
    void costMetricsExposeTotalAttributedAndUnattributedFacts() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-06-02T00:00:00Z");
        when(mapper.selectOverview(from.toLocalDateTime(), to.toLocalDateTime()))
            .thenReturn(new PlatformCostAggregateRow(null, null, null, null, null, "3", "30", "2.50", "2", "25", "2.00"));
        when(mapper.selectDailyTrend(from.toLocalDateTime(), to.toLocalDateTime()))
            .thenReturn(List.of(new PlatformCostAggregateRow(LocalDate.parse("2026-06-01"), null, null, null, null, "3", "30", "2.50", null, null, null)));
        when(mapper.selectModelBreakdown(from.toLocalDateTime(), to.toLocalDateTime()))
            .thenReturn(List.of(new PlatformCostAggregateRow(null, "openai", "gpt-4.1-mini", null, null, "2", "20", "1.75", null, null, null)));
        when(mapper.selectTaskBreakdown(from.toLocalDateTime(), to.toLocalDateTime()))
            .thenReturn(List.of(new PlatformCostAggregateRow(null, null, null, 88L, "任务 A", "2", "25", "2.00", null, null, null)));
        when(mapper.selectOwnerBreakdown(from.toLocalDateTime(), to.toLocalDateTime()))
            .thenReturn(List.of(new PlatformCostAggregateRow(null, null, null, 1001L, "owner_demo", "2", "25", "2.00", null, null, null)));

        PlatformCostMetrics metrics = service.getMetrics(from, to);

        assertThat(metrics.overview().callCount()).isEqualTo(3L);
        assertThat(metrics.overview().totalTokens()).isEqualTo(30L);
        assertThat(metrics.overview().totalCost()).isEqualByComparingTo(new BigDecimal("2.50"));
        assertThat(metrics.overview().attributedCallCount()).isEqualTo(2L);
        assertThat(metrics.overview().attributedTokens()).isEqualTo(25L);
        assertThat(metrics.overview().attributedCost()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(metrics.overview().unattributedCallCount()).isEqualTo(1L);
        assertThat(metrics.overview().unattributedTokens()).isEqualTo(5L);
        assertThat(metrics.overview().unattributedCost()).isEqualByComparingTo(new BigDecimal("0.50"));
        assertThat(metrics.dailyTrend()).hasSize(1);
        assertThat(metrics.modelBreakdown()).hasSize(1);
        assertThat(metrics.taskBreakdown()).hasSize(1);
        assertThat(metrics.ownerBreakdown()).hasSize(1);
        verify(mapper).selectOverview(from.toLocalDateTime(), to.toLocalDateTime());
    }

    @Test
    void costMetricsUseUtcWhenNoRangeIsProvided() {
        when(mapper.selectOverview(null, null))
            .thenReturn(new PlatformCostAggregateRow(null, null, null, null, null, "0", "0", "0.000000", "0", "0", "0.000000"));
        when(mapper.selectDailyTrend(null, null)).thenReturn(List.of());
        when(mapper.selectModelBreakdown(null, null)).thenReturn(List.of());
        when(mapper.selectTaskBreakdown(null, null)).thenReturn(List.of());
        when(mapper.selectOwnerBreakdown(null, null)).thenReturn(List.of());

        PlatformCostMetrics metrics = service.getMetrics(null, null);

        assertThat(metrics.generatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(metrics.isEmpty()).isTrue();
        assertThat(metrics.overview().unattributedCallCount()).isZero();
    }
}
