package com.labelhub.api.module.quality.service.view;

import java.time.LocalDateTime;

public record VerdictView(
    Long submissionId,
    String status,
    Long derivedFromEntryId,
    LocalDateTime derivedAt
) {}
