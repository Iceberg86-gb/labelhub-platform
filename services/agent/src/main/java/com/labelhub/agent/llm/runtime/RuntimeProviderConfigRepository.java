package com.labelhub.agent.llm.runtime;

import java.util.List;
import java.util.Optional;

public interface RuntimeProviderConfigRepository {

    Optional<Long> findOwnerIdBySubmissionId(Long submissionId);

    List<RuntimeProviderConfig> findEnabledByOwnerId(Long ownerId);
}
