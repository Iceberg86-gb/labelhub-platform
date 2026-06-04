package com.labelhub.api.module.ai.providerconfig;

import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmProviderConfigService {
    private static final String PLATFORM_SCOPE = "platform";
    private static final String LLM_PROVIDER_ACTIVATED = "llm_provider_activated";

    private final LlmProviderConfigMapper mapper;
    private final LlmSecretEncryptor encryptor;
    private final LlmProviderConnectionTester connectionTester;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public LlmProviderConfigService(
        LlmProviderConfigMapper mapper,
        LlmSecretEncryptor encryptor,
        LlmProviderConnectionTester connectionTester,
        AuditLogService auditLogService,
        Clock clock
    ) {
        this.mapper = mapper;
        this.encryptor = encryptor;
        this.connectionTester = connectionTester;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LlmProviderConfigEntity> list(Long ownerId) {
        return mapper.selectPlatformProviders();
    }

    @Transactional(readOnly = true)
    public LlmProviderConfigEntity get(Long ownerId, Long id) {
        LlmProviderConfigEntity entity = mapper.selectPlatformById(id);
        if (entity == null) {
            throw new LlmProviderConfigNotFoundException(id);
        }
        return entity;
    }

    @Transactional
    public LlmProviderConfigEntity create(Long ownerId, LlmProviderConfigCreateCommand command) {
        validateRequired(command.providerType(), "providerType");
        validateRequired(command.providerName(), "providerName");
        validateRequired(command.modelName(), "modelName");
        LocalDateTime now = now();
        LlmProviderConfigEntity entity = new LlmProviderConfigEntity();
        entity.setOwnerId(ownerId);
        entity.setScope(PLATFORM_SCOPE);
        entity.setProviderType(command.providerType());
        entity.setProviderName(command.providerName());
        entity.setBaseUrl(blankToNull(command.baseUrl()));
        entity.setModelName(command.modelName());
        entity.setSecretRef(blankToNull(command.secretRef()));
        entity.setEnabled(command.enabled() == null ? Boolean.TRUE : command.enabled());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        applySecret(entity, command.secret(), now);
        mapper.insert(entity);
        audit(AuditActions.LLM_PROVIDER_CONFIG_CREATE, ownerId, entity);
        return entity;
    }

    @Transactional
    public LlmProviderConfigEntity update(Long ownerId, Long id, LlmProviderConfigUpdateCommand command) {
        LlmProviderConfigEntity existing = get(ownerId, id);
        validateRequired(command.providerType(), "providerType");
        validateRequired(command.providerName(), "providerName");
        validateRequired(command.modelName(), "modelName");
        LocalDateTime now = now();
        existing.setProviderType(command.providerType());
        existing.setProviderName(command.providerName());
        existing.setScope(PLATFORM_SCOPE);
        existing.setBaseUrl(blankToNull(command.baseUrl()));
        existing.setModelName(command.modelName());
        existing.setSecretRef(blankToNull(command.secretRef()));
        existing.setEnabled(command.enabled() == null ? Boolean.TRUE : command.enabled());
        existing.setUpdatedAt(now);
        applySecret(existing, command.secret(), now);
        mapper.update(existing);
        audit(AuditActions.LLM_PROVIDER_CONFIG_UPDATE, ownerId, existing);
        return existing;
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        LlmProviderConfigEntity existing = get(ownerId, id);
        mapper.deletePlatformById(id);
        audit(AuditActions.LLM_PROVIDER_CONFIG_DELETE, ownerId, existing);
    }

    @Transactional
    public LlmProviderConfigEntity activate(Long ownerId, Long id) {
        LlmProviderConfigEntity target = get(ownerId, id);
        if (!target.hasSecret()) {
            throw new InvalidLlmProviderConfigException("provider_secret_missing");
        }
        if (Boolean.TRUE.equals(target.getEnabled())) {
            return target;
        }

        List<Long> disabledProviderConfigIds = mapper.disableOtherPlatformProviders(id);
        mapper.enablePlatformProvider(id);
        target.setEnabled(true);
        auditActivation(ownerId, target, disabledProviderConfigIds);
        return target;
    }

    @Transactional
    public LlmProviderConnectionTestResult testSaved(
        Long ownerId,
        Long id,
        LlmProviderConnectionTestCommand request
    ) {
        LlmProviderConfigEntity existing = get(ownerId, id);
        String secret = hasText(request.secret()) ? request.secret() : decryptStoredSecret(existing);
        LlmProviderConnectionTestResult result = connectionTester.test(new LlmProviderConnectionTestCommand(
            valueOrDefault(request.providerType(), existing.getProviderType()),
            valueOrDefault(request.providerName(), existing.getProviderName()),
            valueOrDefault(request.baseUrl(), existing.getBaseUrl()),
            valueOrDefault(request.modelName(), existing.getModelName()),
            secret,
            request.timeout()
        ));
        audit(AuditActions.LLM_PROVIDER_CONFIG_TEST, ownerId, existing, result);
        return result;
    }

    @Transactional
    public LlmProviderConnectionTestResult testUnsaved(Long ownerId, LlmProviderConnectionTestCommand command) {
        validateRequired(command.providerType(), "providerType");
        validateRequired(command.providerName(), "providerName");
        validateRequired(command.modelName(), "modelName");
        LlmProviderConnectionTestResult result = connectionTester.test(command);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerType", command.providerType());
        payload.put("providerName", command.providerName());
        payload.put("modelName", command.modelName());
        payload.put("hasSecret", hasText(command.secret()));
        payload.put("ok", result.ok());
        payload.put("providerStatus", result.providerStatus());
        payload.put("providerCode", result.providerCode());
        auditLogService.record(AuditEventBuilder.forAction(AuditActions.LLM_PROVIDER_CONFIG_TEST)
            .actorPlatformAdmin(ownerId)
            .resource("llm_provider_config", null)
            .payload(SecretRedactor.redact(payload)));
        return result;
    }

    private void applySecret(LlmProviderConfigEntity entity, String secret, LocalDateTime now) {
        if (!hasText(secret)) {
            return;
        }
        EncryptedSecret encrypted = encryptor.encrypt(secret);
        entity.setSecretCiphertext(encrypted.ciphertext());
        entity.setSecretLast4(encrypted.last4());
        entity.setSecretUpdatedAt(now);
    }

    private String decryptStoredSecret(LlmProviderConfigEntity entity) {
        if (!hasText(entity.getSecretCiphertext())) {
            return null;
        }
        return encryptor.decrypt(entity.getSecretCiphertext());
    }

    private void audit(String action, Long ownerId, LlmProviderConfigEntity entity) {
        auditLogService.record(AuditEventBuilder.forAction(action)
            .actorPlatformAdmin(ownerId)
            .resource("llm_provider_config", entity.getId())
            .payload(safePayload(entity)));
    }

    private void audit(String action, Long ownerId, LlmProviderConfigEntity entity, LlmProviderConnectionTestResult result) {
        Map<String, Object> payload = new LinkedHashMap<>(safePayload(entity));
        payload.put("ok", result.ok());
        payload.put("providerStatus", result.providerStatus());
        payload.put("providerCode", result.providerCode());
        auditLogService.record(AuditEventBuilder.forAction(action)
            .actorPlatformAdmin(ownerId)
            .resource("llm_provider_config", entity.getId())
            .payload(SecretRedactor.redact(payload)));
    }

    private void auditActivation(Long ownerId, LlmProviderConfigEntity entity, List<Long> disabledProviderConfigIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerConfigId", entity.getId());
        payload.put("targetProviderConfigId", entity.getId());
        payload.put("disabledProviderConfigIds", disabledProviderConfigIds);
        payload.put("enabled", true);
        auditLogService.record(AuditEventBuilder.forAction(LLM_PROVIDER_ACTIVATED)
            .actorPlatformAdmin(ownerId)
            .resource("llm_provider_config", entity.getId())
            .payload(payload));
    }

    private Map<String, Object> safePayload(LlmProviderConfigEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("providerConfigId", entity.getId());
        payload.put("scope", entity.getScope());
        payload.put("providerType", entity.getProviderType());
        payload.put("providerName", entity.getProviderName());
        payload.put("modelName", entity.getModelName());
        payload.put("enabled", entity.getEnabled());
        payload.put("hasSecret", entity.hasSecret());
        payload.put("secretLast4", entity.getSecretLast4());
        payload.put("secretRef", entity.getSecretRef());
        return SecretRedactor.redact(payload);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private static void validateRequired(String value, String field) {
        if (!hasText(value)) {
            throw new InvalidLlmProviderConfigException(field + " is required");
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
