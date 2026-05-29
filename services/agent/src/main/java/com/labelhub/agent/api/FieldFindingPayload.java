package com.labelhub.agent.api;

import java.math.BigDecimal;

public record FieldFindingPayload(
    String fieldPath,
    String stableId,
    String label,
    String severity,
    String finding,
    BigDecimal confidence
) {
}
