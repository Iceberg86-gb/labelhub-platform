package com.labelhub.api.module.ai.providerconfig;

import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmProviderConfigServiceTest {

    private static final String SECRET = "sk-live-secret-1234567890";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-31T14:20:00Z"), ZoneOffset.UTC);

    private final LlmProviderConfigMapper mapper = mock(LlmProviderConfigMapper.class);
    private final LlmSecretEncryptor encryptor =
        new LlmSecretEncryptor(new LlmSecretProperties("dev-only-llm-provider-master-key-32b"));
    private final LlmProviderConnectionTester connectionTester = mock(LlmProviderConnectionTester.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final LlmProviderConfigService service =
        new LlmProviderConfigService(mapper, encryptor, connectionTester, auditLogService, CLOCK);

    @Test
    void create_encryptsSecretAndAuditPayloadOmitsPlaintextAndCiphertext() {
        when(mapper.insert(any())).thenAnswer(invocation -> {
            LlmProviderConfigEntity entity = invocation.getArgument(0);
            entity.setId(42L);
            return 1;
        });

        service.create(7L, new LlmProviderConfigCreateCommand(
            "openai-compatible",
            "deepseek",
            "https://api.deepseek.test/v1",
            "deepseek-v4-flash",
            SECRET,
            null,
            true
        ));

        ArgumentCaptor<LlmProviderConfigEntity> entityCaptor = ArgumentCaptor.forClass(LlmProviderConfigEntity.class);
        verify(mapper).insert(entityCaptor.capture());
        LlmProviderConfigEntity inserted = entityCaptor.getValue();
        assertThat(inserted.getSecretCiphertext()).isNotBlank();
        assertThat(inserted.getSecretCiphertext()).doesNotContain(SECRET);
        assertThat(inserted.getSecretLast4()).isEqualTo("7890");
        assertThat(inserted.getSecretUpdatedAt()).isEqualTo("2026-05-31T14:20");

        ArgumentCaptor<AuditEventBuilder> auditCaptor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(auditCaptor.capture());
        var event = auditCaptor.getValue().build();
        assertThat(event.actorType()).isEqualTo("platform_admin");
        assertThat(event.actorId()).isEqualTo(7L);
        Map<String, Object> payload = event.payload();
        assertThat(payload.toString()).doesNotContain(SECRET);
        assertThat(payload.toString()).doesNotContain(inserted.getSecretCiphertext());
        assertThat(payload).containsEntry("hasSecret", true);
        assertThat(payload).containsEntry("secretLast4", "7890");
    }

    @Test
    void updateSecret_overwritesCiphertextAndKeepsPlaintextWriteOnly() {
        LlmProviderConfigEntity existing = existingEntity();
        when(mapper.selectByIdAndOwner(42L, 7L)).thenReturn(existing);
        when(mapper.update(any())).thenReturn(1);

        service.update(7L, 42L, new LlmProviderConfigUpdateCommand(
            "openai-compatible",
            "deepseek",
            "https://api.deepseek.test/v1",
            "deepseek-v4-pro",
            SECRET,
            null,
            true
        ));

        ArgumentCaptor<LlmProviderConfigEntity> entityCaptor = ArgumentCaptor.forClass(LlmProviderConfigEntity.class);
        verify(mapper).update(entityCaptor.capture());
        LlmProviderConfigEntity updated = entityCaptor.getValue();
        assertThat(updated.getSecretCiphertext()).isNotEqualTo("old-ciphertext");
        assertThat(updated.getSecretCiphertext()).doesNotContain(SECRET);
        assertThat(updated.getSecretLast4()).isEqualTo("7890");
        assertThat(updated.getSecretUpdatedAt()).isEqualTo("2026-05-31T14:20");
    }

    private LlmProviderConfigEntity existingEntity() {
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        entity.setId(42L);
        entity.setOwnerId(7L);
        entity.setProviderType("openai-compatible");
        entity.setProviderName("deepseek");
        entity.setBaseUrl("https://api.deepseek.test/v1");
        entity.setModelName("deepseek-v4-flash");
        entity.setSecretCiphertext("old-ciphertext");
        entity.setSecretLast4("0000");
        entity.setSecretUpdatedAt(java.time.LocalDateTime.parse("2026-05-30T12:00:00"));
        entity.setEnabled(true);
        return entity;
    }
}
