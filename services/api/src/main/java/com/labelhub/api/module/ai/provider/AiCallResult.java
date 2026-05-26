package com.labelhub.api.module.ai.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiCallResult(
    Map<String, Object> output,
    String overallSuggestion,
    BigDecimal confidence,
    String summary,
    List<FieldFinding> fieldFindings,
    int tokenInput,
    int tokenOutput,
    BigDecimal cost,
    long latencyMs,
    String rawResponse
) {}
