package com.labelhub.api.module.platform.service;

import com.labelhub.api.module.platform.labor.PlatformLaborMetricRow;
import com.labelhub.api.module.platform.labor.PlatformLaborMetrics;
import com.labelhub.api.module.platform.labor.PlatformLaborMetricsMapper;
import com.labelhub.api.module.platform.labor.PlatformLaborMetricsService;
import com.labelhub.api.module.platform.labor.PlatformReworkMetrics;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformLaborMetricsServiceTest {

    private final PlatformLaborMetricsMapper mapper = mock(PlatformLaborMetricsMapper.class);
    private final PlatformLaborMetricsService service = new PlatformLaborMetricsService(mapper);

    @Test
    void laborMetricsExposeSubmissionReviewAndThreeReworkFacts() {
        when(mapper.selectSubmissionMetrics()).thenReturn(List.of(
            PlatformLaborMetricRow.forUser(1002L, "标注员", "labeler_demo", 7L)
        ));
        when(mapper.selectReviewMetrics()).thenReturn(List.of(
            PlatformLaborMetricRow.forReviewer(1003L, "审核员", "reviewer_demo", 5L, 3L, 2L, 1L, 4L, 0L)
        ));
        when(mapper.selectReworkMetrics()).thenReturn(new PlatformReworkMetrics(2L, 3L, 1L));

        PlatformLaborMetrics metrics = service.getMetrics();

        assertThat(metrics.generatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(metrics.empty()).isFalse();
        assertThat(metrics.submissions()).singleElement()
            .satisfies(row -> {
                assertThat(row.userId()).isEqualTo(1002L);
                assertThat(row.count()).isEqualTo(7L);
            });
        assertThat(metrics.reviews()).singleElement()
            .satisfies(row -> {
                assertThat(row.userId()).isEqualTo(1003L);
                assertThat(row.count()).isEqualTo(5L);
                assertThat(row.initialReviewCount()).isEqualTo(3L);
                assertThat(row.seniorReviewCount()).isEqualTo(2L);
            });
        assertThat(metrics.rework().supersededSubmissionCount()).isEqualTo(2L);
        assertThat(metrics.rework().multiRoundReviewActionCount()).isEqualTo(3L);
        assertThat(metrics.rework().returnedForRevisionSubmissionCount()).isEqualTo(1L);
    }

    @Test
    void laborMetricsAreEmptyOnlyWhenAllFactsAreEmpty() {
        when(mapper.selectSubmissionMetrics()).thenReturn(List.of());
        when(mapper.selectReviewMetrics()).thenReturn(List.of());
        when(mapper.selectReworkMetrics()).thenReturn(new PlatformReworkMetrics(0L, 0L, 0L));

        PlatformLaborMetrics metrics = service.getMetrics();

        assertThat(metrics.empty()).isTrue();
    }
}
