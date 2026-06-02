package com.labelhub.api.module.ai.providerconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.LlmProviderConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderConfigDtoMapperTest {

    private final LlmProviderConfigDtoMapper mapper = new LlmProviderConfigDtoMapper();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void toDto_exposesOnlySecretMetadataWhenSerialized() throws Exception {
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        entity.setId(11L);
        entity.setOwnerId(1L);
        entity.setScope("platform");
        entity.setProviderType("openai-compatible");
        entity.setProviderName("deepseek");
        entity.setBaseUrl("https://api.example.test/v1");
        entity.setModelName("deepseek-v4-flash");
        entity.setSecretCiphertext("ciphertext-containing-sk-test-secret-1234567890");
        entity.setSecretLast4("7890");
        entity.setSecretUpdatedAt(LocalDateTime.parse("2026-05-31T10:15:30"));
        entity.setSecretRef("vault://future/ref");
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.parse("2026-05-31T10:00:00"));
        entity.setUpdatedAt(LocalDateTime.parse("2026-05-31T10:15:30"));

        var dto = mapper.toDto(entity);
        String json = objectMapper.writeValueAsString(dto);

        assertThat(dto.getHasSecret()).isTrue();
        assertThat(dto.getScope()).isEqualTo(LlmProviderConfig.ScopeEnum.PLATFORM);
        assertThat(dto.getSecretLast4()).isEqualTo("7890");
        assertThat(json).contains("secretLast4");
        assertThat(json).doesNotContain("sk-test-secret");
        assertThat(json).doesNotContain("ciphertext");
        assertThat(json).doesNotContain("secretCiphertext");
    }
}
