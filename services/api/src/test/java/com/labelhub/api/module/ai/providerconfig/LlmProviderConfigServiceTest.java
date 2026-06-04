package com.labelhub.api.module.ai.providerconfig;

import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertThat(inserted.getScope()).isEqualTo("platform");
        assertThat(inserted.getOwnerId()).isEqualTo(7L);
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
        assertThat(payload).containsEntry("scope", "platform");
        assertThat(payload).containsEntry("hasSecret", true);
        assertThat(payload).containsEntry("secretLast4", "7890");
    }

    @Test
    void listReadsPlatformProvidersRatherThanActorOwnedProviders() {
        service.list(7L);

        verify(mapper).selectPlatformProviders();
    }

    @Test
    void updateSecret_overwritesCiphertextAndKeepsPlaintextWriteOnly() {
        LlmProviderConfigEntity existing = existingEntity();
        when(mapper.selectPlatformById(42L)).thenReturn(existing);
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
        assertThat(updated.getScope()).isEqualTo("platform");
        assertThat(updated.getSecretCiphertext()).isNotEqualTo("old-ciphertext");
        assertThat(updated.getSecretCiphertext()).doesNotContain(SECRET);
        assertThat(updated.getSecretLast4()).isEqualTo("7890");
        assertThat(updated.getSecretUpdatedAt()).isEqualTo("2026-05-31T14:20");
    }

    @Test
    void activate_disablesOtherPlatformProvidersThenEnablesTargetAndAuditsIdsOnly() throws Exception {
        LlmProviderConfigEntity target = existingEntity();
        target.setId(43L);
        target.setEnabled(false);
        when(mapper.selectPlatformById(43L)).thenReturn(target);
        when(mapper.disableOtherPlatformProviders(43L)).thenReturn(List.of(42L, 44L));
        when(mapper.enablePlatformProvider(43L)).thenReturn(1);

        LlmProviderConfigEntity activated = service.activate(7L, 43L);

        assertThat(activated).isSameAs(target);
        assertThat(activated.getEnabled()).isTrue();
        verify(mapper).disableOtherPlatformProviders(43L);
        verify(mapper).enablePlatformProvider(43L);

        var activateMethod = LlmProviderConfigService.class.getMethod("activate", Long.class, Long.class);
        assertThat(activateMethod.getAnnotation(Transactional.class)).isNotNull();

        ArgumentCaptor<AuditEventBuilder> auditCaptor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService).record(auditCaptor.capture());
        var event = auditCaptor.getValue().build();
        assertThat(event.action()).isEqualTo("llm_provider_activated");
        assertThat(event.actorType()).isEqualTo("platform_admin");
        assertThat(event.actorId()).isEqualTo(7L);
        assertThat(event.resourceType()).isEqualTo("llm_provider_config");
        assertThat(event.resourceId()).isEqualTo(43L);
        Map<String, Object> payload = event.payload();
        assertThat(payload).containsEntry("providerConfigId", 43L);
        assertThat(payload).containsEntry("targetProviderConfigId", 43L);
        assertThat(payload).containsEntry("disabledProviderConfigIds", List.of(42L, 44L));
        assertThat(payload).containsEntry("enabled", true);
        assertThat(payload.toString())
            .doesNotContain("old-ciphertext")
            .doesNotContain("secret")
            .doesNotContain("secretRef")
            .doesNotContain("0000");
    }

    @Test
    void activate_isIdempotentWhenTargetAlreadyEnabled() {
        LlmProviderConfigEntity target = existingEntity();
        target.setId(43L);
        target.setEnabled(true);
        when(mapper.selectPlatformById(43L)).thenReturn(target);

        LlmProviderConfigEntity activated = service.activate(7L, 43L);

        assertThat(activated).isSameAs(target);
        verify(mapper, never()).disableOtherPlatformProviders(43L);
        verify(mapper, never()).enablePlatformProvider(43L);
    }

    @Test
    void activate_rejectsProviderWithoutStoredSecretOrSecretRef() {
        LlmProviderConfigEntity target = existingEntity();
        target.setId(43L);
        target.setSecretCiphertext(null);
        target.setSecretRef(null);
        when(mapper.selectPlatformById(43L)).thenReturn(target);

        assertThatThrownBy(() -> service.activate(7L, 43L))
            .isInstanceOf(InvalidLlmProviderConfigException.class)
            .hasMessage("provider_secret_missing");

        verify(mapper, never()).disableOtherPlatformProviders(43L);
        verify(mapper, never()).enablePlatformProvider(43L);
        verify(auditLogService, never()).record(any());
    }

    @Test
    void activate_allowsSecretRefAsSecretSourceWithoutDecryptingIt() {
        LlmProviderConfigEntity target = existingEntity();
        target.setId(43L);
        target.setEnabled(false);
        target.setSecretCiphertext(null);
        target.setSecretLast4(null);
        target.setSecretRef("vault://future-provider");
        when(mapper.selectPlatformById(43L)).thenReturn(target);
        when(mapper.disableOtherPlatformProviders(43L)).thenReturn(List.of());
        when(mapper.enablePlatformProvider(43L)).thenReturn(1);

        service.activate(7L, 43L);

        verify(mapper).disableOtherPlatformProviders(43L);
        verify(mapper).enablePlatformProvider(43L);
    }

    private LlmProviderConfigEntity existingEntity() {
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        entity.setId(42L);
        entity.setOwnerId(7L);
        entity.setScope("platform");
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
