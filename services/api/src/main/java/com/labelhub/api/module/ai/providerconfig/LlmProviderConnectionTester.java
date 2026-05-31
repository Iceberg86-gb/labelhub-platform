package com.labelhub.api.module.ai.providerconfig;

public interface LlmProviderConnectionTester {
    LlmProviderConnectionTestResult test(LlmProviderConnectionTestCommand command);
}
