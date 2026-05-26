package com.labelhub.api.module.ai.provider;

import java.time.Duration;
import java.util.Map;

public record AiCallRequest(
    String promptVersion,
    Map<String, Object> input,
    Duration timeout
) {}
