package com.labelhub.agent.llm.runtime;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeProviderResolverTest {

    private static final String MASTER_KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void resolves_env_fallback_when_owner_has_no_enabled_db_provider() {
        RuntimeProviderConfigRepository repository = repository(55L, List.of());
        RuntimeProviderResolver resolver = new RuntimeProviderResolver(
            repository,
            ciphertext -> {
                throw new AssertionError("decrypt should not be called for env fallback");
            },
            Optional.of(envSource())
        );

        RuntimeProviderSource source = resolver.resolve(300L);

        assertThat(source.source()).isEqualTo(RuntimeProviderSource.Source.ENV);
        assertThat(source.providerName()).isEqualTo("doubao");
        assertThat(source.apiKey()).isEqualTo("env-secret");
    }

    @Test
    void resolves_single_enabled_db_provider_and_decrypts_secret_in_memory() {
        RuntimeProviderConfigRepository repository = repository(55L, List.of(config("ciphertext-value")));
        RuntimeProviderResolver resolver = new RuntimeProviderResolver(
            repository,
            ciphertext -> "db-secret-value",
            Optional.of(envSource())
        );

        RuntimeProviderSource source = resolver.resolve(300L);

        assertThat(source.source()).isEqualTo(RuntimeProviderSource.Source.DB);
        assertThat(source.providerConfigId()).isEqualTo(9L);
        assertThat(source.apiKey()).isEqualTo("db-secret-value");
        assertThat(source.providerName()).isEqualTo("owner-doubao");
    }

    @Test
    void multiple_enabled_db_providers_is_permanent_config_error_without_env_fallback() {
        RuntimeProviderConfigRepository repository = repository(55L, List.of(config("a"), config("b")));
        RuntimeProviderResolver resolver = new RuntimeProviderResolver(
            repository,
            ciphertext -> "db-secret-value",
            Optional.of(envSource())
        );

        assertThatThrownBy(() -> resolver.resolve(300L))
            .isInstanceOf(RuntimeProviderResolutionException.class)
            .hasMessageContaining("multiple enabled")
            .hasMessageNotContaining("env-secret");
    }

    @Test
    void decrypt_failure_is_permanent_config_error_without_env_fallback_or_key_leak() {
        RuntimeProviderConfigRepository repository = repository(55L, List.of(config("ciphertext-value")));
        RuntimeProviderResolver resolver = new RuntimeProviderResolver(
            repository,
            ciphertext -> {
                throw new IllegalStateException("Failed to decrypt LLM provider secret");
            },
            Optional.of(envSource())
        );

        assertThatThrownBy(() -> resolver.resolve(300L))
            .isInstanceOf(RuntimeProviderResolutionException.class)
            .hasMessageContaining("decrypt")
            .hasMessageNotContaining("env-secret")
            .hasMessageNotContaining("ciphertext-value");
    }

    @Test
    void secret_ref_without_ciphertext_is_not_readable_at_runtime() {
        RuntimeProviderConfig config = new RuntimeProviderConfig(
            9L,
            55L,
            "openai-compatible",
            "owner-doubao",
            "https://example.test/v1",
            "doubao-lite",
            null,
            "vault://future-key",
            true
        );
        RuntimeProviderResolver resolver = new RuntimeProviderResolver(
            repository(55L, List.of(config)),
            ciphertext -> "db-secret-value",
            Optional.of(envSource())
        );

        assertThatThrownBy(() -> resolver.resolve(300L))
            .isInstanceOf(RuntimeProviderResolutionException.class)
            .hasMessageContaining("secretRef")
            .hasMessageNotContaining("vault://future-key");
    }

    @Test
    void runtime_provider_records_do_not_leak_secret_material_in_to_string() {
        RuntimeProviderConfig config = config("ciphertext-value");
        RuntimeProviderSource source = RuntimeProviderSource.db(
            9L,
            "openai-compatible",
            "owner-doubao",
            "https://example.test/v1",
            "doubao-lite",
            "db-secret-value"
        );

        assertThat(config.toString())
            .doesNotContain("ciphertext-value")
            .doesNotContain("secretCiphertext");
        assertThat(source.toString())
            .doesNotContain("db-secret-value")
            .doesNotContain("apiKey");
    }

    private static RuntimeProviderConfigRepository repository(Long ownerId, List<RuntimeProviderConfig> configs) {
        return new RuntimeProviderConfigRepository() {
            @Override
            public Optional<Long> findOwnerIdBySubmissionId(Long submissionId) {
                return Optional.of(ownerId);
            }

            @Override
            public List<RuntimeProviderConfig> findEnabledByOwnerId(Long ownerId) {
                return configs;
            }
        };
    }

    private static RuntimeProviderConfig config(String ciphertext) {
        return new RuntimeProviderConfig(
            9L,
            55L,
            "openai-compatible",
            "owner-doubao",
            "https://example.test/v1",
            "doubao-lite",
            ciphertext,
            null,
            true
        );
    }

    private static RuntimeProviderSource envSource() {
        return RuntimeProviderSource.env(
            "openai-compatible",
            "doubao",
            "https://env.example.test/v1",
            "env-model",
            "env-secret"
        );
    }
}
