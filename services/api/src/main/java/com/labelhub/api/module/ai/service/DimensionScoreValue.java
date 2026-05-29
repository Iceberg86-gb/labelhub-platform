package com.labelhub.api.module.ai.service;

import java.math.BigDecimal;

public record DimensionScoreValue(
    String dimension,
    BigDecimal score,
    String reason
) {
}
