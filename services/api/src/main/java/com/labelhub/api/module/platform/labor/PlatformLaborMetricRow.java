package com.labelhub.api.module.platform.labor;

public class PlatformLaborMetricRow {

    private Long userId;
    private String displayName;
    private String username;
    private Long count = 0L;
    private Long initialReviewCount = 0L;
    private Long seniorReviewCount = 0L;
    private Long approveActionCount = 0L;
    private Long returnActionCount = 0L;
    private Long rejectActionCount = 0L;

    public PlatformLaborMetricRow() {
    }

    public static PlatformLaborMetricRow forUser(Long userId, String displayName, String username, Long count) {
        PlatformLaborMetricRow row = new PlatformLaborMetricRow();
        row.userId = userId;
        row.displayName = displayName;
        row.username = username;
        row.count = count;
        return row;
    }

    public static PlatformLaborMetricRow forReviewer(
        Long userId,
        String displayName,
        String username,
        Long count,
        Long initialReviewCount,
        Long seniorReviewCount,
        Long approveActionCount,
        Long returnActionCount,
        Long rejectActionCount
    ) {
        PlatformLaborMetricRow row = forUser(userId, displayName, username, count);
        row.initialReviewCount = initialReviewCount;
        row.seniorReviewCount = seniorReviewCount;
        row.approveActionCount = approveActionCount;
        row.returnActionCount = returnActionCount;
        row.rejectActionCount = rejectActionCount;
        return row;
    }

    public Long userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }

    public String username() {
        return username;
    }

    public Long count() {
        return count == null ? 0L : count;
    }

    public Long initialReviewCount() {
        return initialReviewCount == null ? 0L : initialReviewCount;
    }

    public Long seniorReviewCount() {
        return seniorReviewCount == null ? 0L : seniorReviewCount;
    }

    public Long approveActionCount() {
        return approveActionCount == null ? 0L : approveActionCount;
    }

    public Long returnActionCount() {
        return returnActionCount == null ? 0L : returnActionCount;
    }

    public Long rejectActionCount() {
        return rejectActionCount == null ? 0L : rejectActionCount;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public void setInitialReviewCount(Long initialReviewCount) {
        this.initialReviewCount = initialReviewCount;
    }

    public void setSeniorReviewCount(Long seniorReviewCount) {
        this.seniorReviewCount = seniorReviewCount;
    }

    public void setApproveActionCount(Long approveActionCount) {
        this.approveActionCount = approveActionCount;
    }

    public void setReturnActionCount(Long returnActionCount) {
        this.returnActionCount = returnActionCount;
    }

    public void setRejectActionCount(Long rejectActionCount) {
        this.rejectActionCount = rejectActionCount;
    }
}
