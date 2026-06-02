package com.labelhub.api.module.platform.efficiency;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PlatformUnitCostMetrics {

    private BigDecimal totalCost = BigDecimal.ZERO;
    private Long distinctSubmissionCount = 0L;
    private Long distinctDatasetItemCount = 0L;
    private BigDecimal costPerSubmission = BigDecimal.ZERO;
    private BigDecimal costPerDatasetItem = BigDecimal.ZERO;

    public PlatformUnitCostMetrics() {
    }

    public PlatformUnitCostMetrics(BigDecimal totalCost, Long distinctSubmissionCount, Long distinctDatasetItemCount) {
        this.totalCost = totalCost == null ? BigDecimal.ZERO : totalCost;
        this.distinctSubmissionCount = distinctSubmissionCount;
        this.distinctDatasetItemCount = distinctDatasetItemCount;
        recalculate();
    }

    public BigDecimal totalCost() {
        return totalCost == null ? BigDecimal.ZERO : totalCost;
    }

    public Long distinctSubmissionCount() {
        return distinctSubmissionCount == null ? 0L : distinctSubmissionCount;
    }

    public Long distinctDatasetItemCount() {
        return distinctDatasetItemCount == null ? 0L : distinctDatasetItemCount;
    }

    public BigDecimal costPerSubmission() {
        return costPerSubmission == null ? BigDecimal.ZERO : costPerSubmission;
    }

    public BigDecimal costPerDatasetItem() {
        return costPerDatasetItem == null ? BigDecimal.ZERO : costPerDatasetItem;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
        recalculate();
    }

    public void setDistinctSubmissionCount(Long distinctSubmissionCount) {
        this.distinctSubmissionCount = distinctSubmissionCount;
        recalculate();
    }

    public void setDistinctDatasetItemCount(Long distinctDatasetItemCount) {
        this.distinctDatasetItemCount = distinctDatasetItemCount;
        recalculate();
    }

    public void setCostPerSubmission(BigDecimal costPerSubmission) {
        this.costPerSubmission = costPerSubmission;
    }

    public void setCostPerDatasetItem(BigDecimal costPerDatasetItem) {
        this.costPerDatasetItem = costPerDatasetItem;
    }

    private void recalculate() {
        this.costPerSubmission = divide(totalCost(), distinctSubmissionCount());
        this.costPerDatasetItem = divide(totalCost(), distinctDatasetItemCount());
    }

    private static BigDecimal divide(BigDecimal value, long denominator) {
        if (value == null || denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }
}
