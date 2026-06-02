package com.labelhub.api.module.ai.prereview;

public record AiPrereviewSignalsView(
    Long submissionId,
    String status,
    AiPrereviewSignals signals
) {
}
