package com.labelhub.api.module.ai.providerconfig;

import java.time.Duration;

public record LlmProviderConnectionTestCommand(
    String providerType,
    String providerName,
    String baseUrl,
    String modelName,
    String secret,
    Duration timeout
) {
}
