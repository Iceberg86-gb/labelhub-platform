package com.labelhub.api.module.ai.prereview;

public record AiPrereviewSignals(
    String outboxStatus,
    String aiCallStatus,
    boolean hasAiOverallRecommendation
) {
}
