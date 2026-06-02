package com.labelhub.api.module.ai.providerconfig;

import com.labelhub.api.generated.model.LlmProviderConfig;
import com.labelhub.api.generated.model.LlmProviderTestConnectionResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class LlmProviderConfigDtoMapper {

    public LlmProviderConfig toDto(LlmProviderConfigEntity entity) {
        LlmProviderConfig dto = new LlmProviderConfig();
        dto.setId(entity.getId());
        dto.setScope(LlmProviderConfig.ScopeEnum.fromValue(entity.getScope()));
        dto.setProviderType(entity.getProviderType());
        dto.setProviderName(entity.getProviderName());
        dto.setBaseUrl(entity.getBaseUrl());
        dto.setModelName(entity.getModelName());
        dto.setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
        dto.setHasSecret(entity.hasSecret());
        dto.setSecretLast4(entity.getSecretLast4());
        dto.setSecretUpdatedAt(toOffset(entity.getSecretUpdatedAt()));
        dto.setSecretRef(entity.getSecretRef());
        dto.setCreatedAt(toOffset(entity.getCreatedAt()));
        dto.setUpdatedAt(toOffset(entity.getUpdatedAt()));
        return dto;
    }

    public LlmProviderTestConnectionResponse toDto(LlmProviderConnectionTestResult result) {
        LlmProviderTestConnectionResponse dto = new LlmProviderTestConnectionResponse();
        dto.setOk(result.ok());
        dto.setProviderName(result.providerName());
        dto.setModelName(result.modelName());
        dto.setLatencyMs(result.latencyMs());
        dto.setProviderStatus(result.providerStatus());
        dto.setProviderCode(result.providerCode());
        dto.setMessage(result.message());
        return dto;
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
