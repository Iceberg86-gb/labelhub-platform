package com.labelhub.agent.api;

import java.math.BigDecimal;

public record DimensionScorePayload(String dimension, BigDecimal score, String reason) {
}
