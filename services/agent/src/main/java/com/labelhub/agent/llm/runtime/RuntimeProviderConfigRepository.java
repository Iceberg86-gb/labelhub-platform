package com.labelhub.agent.llm.runtime;

import java.util.List;

public interface RuntimeProviderConfigRepository {

    List<RuntimeProviderConfig> findEnabledPlatformProviders();
}
