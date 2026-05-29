package com.labelhub.api.module.ai.provider;

import java.time.Duration;
import java.util.Map;

public record AiCallRequest(
    String promptVersion,
    String businessPrompt,
    Map<String, Object> input,
    Duration timeout
) {
    public AiCallRequest(String promptVersion, Map<String, Object> input, Duration timeout) {
        this(promptVersion, "", input, timeout);
    }
}
