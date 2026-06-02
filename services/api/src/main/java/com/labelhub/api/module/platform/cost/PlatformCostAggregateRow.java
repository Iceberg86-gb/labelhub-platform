package com.labelhub.api.module.platform.cost;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PlatformCostAggregateRow {

    private LocalDate bucketDate;
    private String modelProvider;
    private String modelName;
    private Long groupId;
    private String groupName;
    private Long callCount;
    private Long totalTokens;
    private BigDecimal totalCost;
    private Long attributedCallCount;
    private Long attributedTokens;
    private BigDecimal attributedCost;

    public PlatformCostAggregateRow() {
    }

    public PlatformCostAggregateRow(
        LocalDate bucketDate,
        String modelProvider,
        String modelName,
        Long groupId,
        String groupName,
        String callCount,
        String totalTokens,
        String totalCost,
        String attributedCallCount,
        String attributedTokens,
        String attributedCost
    ) {
        this.bucketDate = bucketDate;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.groupId = groupId;
        this.groupName = groupName;
        this.callCount = parseLong(callCount);
        this.totalTokens = parseLong(totalTokens);
        this.totalCost = parseDecimal(totalCost);
        this.attributedCallCount = parseLong(attributedCallCount);
        this.attributedTokens = parseLong(attributedTokens);
        this.attributedCost = parseDecimal(attributedCost);
    }

    public LocalDate getBucketDate() { return bucketDate; }
    public void setBucketDate(LocalDate bucketDate) { this.bucketDate = bucketDate; }
    public String getModelProvider() { return modelProvider; }
    public void setModelProvider(String modelProvider) { this.modelProvider = modelProvider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Long getCallCount() { return callCount; }
    public void setCallCount(Long callCount) { this.callCount = callCount; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public Long getAttributedCallCount() { return attributedCallCount; }
    public void setAttributedCallCount(Long attributedCallCount) { this.attributedCallCount = attributedCallCount; }
    public Long getAttributedTokens() { return attributedTokens; }
    public void setAttributedTokens(Long attributedTokens) { this.attributedTokens = attributedTokens; }
    public BigDecimal getAttributedCost() { return attributedCost; }
    public void setAttributedCost(BigDecimal attributedCost) { this.attributedCost = attributedCost; }

    private static Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private static BigDecimal parseDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
