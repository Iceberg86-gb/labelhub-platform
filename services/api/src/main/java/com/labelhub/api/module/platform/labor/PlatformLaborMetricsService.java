package com.labelhub.api.module.platform.labor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformLaborMetricsService {

    private final PlatformLaborMetricsMapper mapper;

    public PlatformLaborMetricsService(PlatformLaborMetricsMapper mapper) {
        this.mapper = mapper;
    }

    public PlatformLaborMetrics getMetrics() {
        List<PlatformLaborMetricRow> submissions = mapper.selectSubmissionMetrics();
        List<PlatformLaborMetricRow> reviews = mapper.selectReviewMetrics();
        PlatformReworkMetrics rework = mapper.selectReworkMetrics();
        boolean empty = submissions.isEmpty()
            && reviews.isEmpty()
            && rework.supersededSubmissionCount() == 0
            && rework.multiRoundReviewActionCount() == 0
            && rework.returnedForRevisionSubmissionCount() == 0;
        return new PlatformLaborMetrics(OffsetDateTime.now(ZoneOffset.UTC), submissions, reviews, rework, empty);
    }
}
