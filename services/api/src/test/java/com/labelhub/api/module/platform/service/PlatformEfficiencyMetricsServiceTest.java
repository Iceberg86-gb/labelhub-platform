package com.labelhub.api.module.platform.service;

import com.labelhub.api.module.platform.efficiency.PlatformEfficiencyMetrics;
import com.labelhub.api.module.platform.efficiency.PlatformEfficiencyMetricsMapper;
import com.labelhub.api.module.platform.efficiency.PlatformIdempotencyMetrics;
import com.labelhub.api.module.platform.efficiency.PlatformUnitCostMetrics;
import com.labelhub.api.module.platform.efficiency.PlatformEfficiencyMetricsService;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformEfficiencyMetricsServiceTest {

    private final PlatformEfficiencyMetricsMapper mapper = mock(PlatformEfficiencyMetricsMapper.class);
    private final PlatformEfficiencyMetricsService service = new PlatformEfficiencyMetricsService(mapper);

    @Test
    void efficiencyMetricsExposeIdempotencyAndUnitCostFacts() {
        when(mapper.selectIdempotencyMetrics())
            .thenReturn(new PlatformIdempotencyMetrics(4L, 3L, 1L, 18L));
        when(mapper.selectUnitCostMetrics())
            .thenReturn(new PlatformUnitCostMetrics(new BigDecimal("2.400000"), 4L, 2L));

        PlatformEfficiencyMetrics metrics = service.getMetrics();

        assertThat(metrics.generatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(metrics.empty()).isFalse();
        assertThat(metrics.idempotency().callCount()).isEqualTo(4L);
        assertThat(metrics.idempotency().duplicateKeyCount()).isEqualTo(1L);
        assertThat(metrics.idempotency().cacheHitTokens()).isEqualTo(18L);
        assertThat(metrics.unitCost().totalCost()).isEqualByComparingTo("2.400000");
        assertThat(metrics.unitCost().distinctSubmissionCount()).isEqualTo(4L);
        assertThat(metrics.unitCost().costPerSubmission()).isEqualByComparingTo("0.600000");
        assertThat(metrics.unitCost().distinctDatasetItemCount()).isEqualTo(2L);
        assertThat(metrics.unitCost().costPerDatasetItem()).isEqualByComparingTo("1.200000");
    }

    @Test
    void efficiencyMetricsAreEmptyWhenAiCallsAreEmpty() {
        when(mapper.selectIdempotencyMetrics())
            .thenReturn(new PlatformIdempotencyMetrics(0L, 0L, 0L, 0L));
        when(mapper.selectUnitCostMetrics())
            .thenReturn(new PlatformUnitCostMetrics(BigDecimal.ZERO, 0L, 0L));

        PlatformEfficiencyMetrics metrics = service.getMetrics();

        assertThat(metrics.empty()).isTrue();
    }
}
