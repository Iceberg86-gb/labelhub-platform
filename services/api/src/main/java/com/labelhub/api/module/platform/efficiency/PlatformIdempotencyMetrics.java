package com.labelhub.api.module.platform.efficiency;

public class PlatformIdempotencyMetrics {

    private Long callCount = 0L;
    private Long uniqueKeyCount = 0L;
    private Long duplicateKeyCount = 0L;
    private Long cacheHitTokens = 0L;

    public PlatformIdempotencyMetrics() {
    }

    public PlatformIdempotencyMetrics(Long callCount, Long uniqueKeyCount, Long duplicateKeyCount, Long cacheHitTokens) {
        this.callCount = callCount;
        this.uniqueKeyCount = uniqueKeyCount;
        this.duplicateKeyCount = duplicateKeyCount;
        this.cacheHitTokens = cacheHitTokens;
    }

    public Long callCount() {
        return callCount == null ? 0L : callCount;
    }

    public Long uniqueKeyCount() {
        return uniqueKeyCount == null ? 0L : uniqueKeyCount;
    }

    public Long duplicateKeyCount() {
        return duplicateKeyCount == null ? 0L : duplicateKeyCount;
    }

    public Long cacheHitTokens() {
        return cacheHitTokens == null ? 0L : cacheHitTokens;
    }

    public void setCallCount(Long callCount) {
        this.callCount = callCount;
    }

    public void setUniqueKeyCount(Long uniqueKeyCount) {
        this.uniqueKeyCount = uniqueKeyCount;
    }

    public void setDuplicateKeyCount(Long duplicateKeyCount) {
        this.duplicateKeyCount = duplicateKeyCount;
    }

    public void setCacheHitTokens(Long cacheHitTokens) {
        this.cacheHitTokens = cacheHitTokens;
    }
}
