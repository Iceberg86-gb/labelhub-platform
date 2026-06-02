package com.labelhub.api.module.platform.labor;

public class PlatformReworkMetrics {

    private Long supersededSubmissionCount = 0L;
    private Long multiRoundReviewActionCount = 0L;
    private Long returnedForRevisionSubmissionCount = 0L;

    public PlatformReworkMetrics() {
    }

    public PlatformReworkMetrics(
        Long supersededSubmissionCount,
        Long multiRoundReviewActionCount,
        Long returnedForRevisionSubmissionCount
    ) {
        this.supersededSubmissionCount = supersededSubmissionCount;
        this.multiRoundReviewActionCount = multiRoundReviewActionCount;
        this.returnedForRevisionSubmissionCount = returnedForRevisionSubmissionCount;
    }

    public Long supersededSubmissionCount() {
        return supersededSubmissionCount == null ? 0L : supersededSubmissionCount;
    }

    public Long multiRoundReviewActionCount() {
        return multiRoundReviewActionCount == null ? 0L : multiRoundReviewActionCount;
    }

    public Long returnedForRevisionSubmissionCount() {
        return returnedForRevisionSubmissionCount == null ? 0L : returnedForRevisionSubmissionCount;
    }

    public void setSupersededSubmissionCount(Long supersededSubmissionCount) {
        this.supersededSubmissionCount = supersededSubmissionCount;
    }

    public void setMultiRoundReviewActionCount(Long multiRoundReviewActionCount) {
        this.multiRoundReviewActionCount = multiRoundReviewActionCount;
    }

    public void setReturnedForRevisionSubmissionCount(Long returnedForRevisionSubmissionCount) {
        this.returnedForRevisionSubmissionCount = returnedForRevisionSubmissionCount;
    }
}
