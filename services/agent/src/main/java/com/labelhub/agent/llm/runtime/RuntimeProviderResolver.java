package com.labelhub.agent.llm.runtime;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class RuntimeProviderResolver {

    private final RuntimeProviderConfigRepository repository;
    private final Function<String, String> decryptor;
    private final Optional<RuntimeProviderSource> envFallback;

    @Autowired
    public RuntimeProviderResolver(
        RuntimeProviderConfigRepository repository,
        AgentLlmSecretDecryptor decryptor,
        EnvRuntimeProviderSourceFactory envFallbackFactory
    ) {
        this(repository, decryptor::decrypt, envFallbackFactory.source());
    }

    RuntimeProviderResolver(
        RuntimeProviderConfigRepository repository,
        Function<String, String> decryptor,
        Optional<RuntimeProviderSource> envFallback
    ) {
        this.repository = repository;
        this.decryptor = decryptor;
        this.envFallback = envFallback;
    }

    public RuntimeProviderSource resolve(Long submissionId) {
        List<RuntimeProviderConfig> configs = repository.findEnabledPlatformProviders();
        if (configs.isEmpty()) {
            return envFallback.orElseThrow(() -> new RuntimeProviderResolutionException(
                "No enabled DB provider and env fallback is not configured",
                "provider_not_configured"
            ));
        }
        if (configs.size() > 1) {
            throw new RuntimeProviderResolutionException(
                "Platform has multiple enabled LLM provider configs; runtime supports exactly one enabled provider",
                "multiple_enabled_providers"
            );
        }
        return dbSource(configs.get(0));
    }

    private RuntimeProviderSource dbSource(RuntimeProviderConfig config) {
        requireText(config.providerType(), "provider type", config.id());
        requireText(config.providerName(), "provider name", config.id());
        requireText(config.baseUrl(), "provider base URL", config.id());
        requireText(config.modelName(), "provider model", config.id());
        String secret = decryptSecret(config);
        if (!hasText(secret)) {
            throw new RuntimeProviderResolutionException(
                "Runtime provider secret is missing for config " + config.id(),
                "provider_secret_missing"
            );
        }
        return RuntimeProviderSource.db(
            config.id(),
            config.providerType(),
            config.providerName(),
            config.baseUrl(),
            config.modelName(),
            secret
        );
    }

    private String decryptSecret(RuntimeProviderConfig config) {
        if (!hasText(config.secretCiphertext())) {
            if (hasText(config.secretRef())) {
                throw new RuntimeProviderResolutionException(
                    "Runtime provider has secretRef but no decryptable ciphertext; secretRef runtime lookup is not supported in Batch B",
                    "secret_ref_unsupported"
                );
            }
            return null;
        }
        try {
            return decryptor.apply(config.secretCiphertext());
        } catch (RuntimeException exception) {
            throw new RuntimeProviderResolutionException(
                "Failed to decrypt runtime provider secret for config " + config.id(),
                "provider_secret_decrypt_failed",
                exception
            );
        }
    }

    private void requireText(String value, String label, Long configId) {
        if (!hasText(value)) {
            throw new RuntimeProviderResolutionException(
                "Runtime provider " + label + " is required for config " + configId,
                "provider_config_invalid"
            );
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
