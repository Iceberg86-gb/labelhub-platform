package com.labelhub.api.module.ai.provider;

import java.math.BigDecimal;

public record FieldFinding(
    String fieldPath,
    String stableId,
    String label,
    String severity,
    String finding,
    BigDecimal confidence
) {}
