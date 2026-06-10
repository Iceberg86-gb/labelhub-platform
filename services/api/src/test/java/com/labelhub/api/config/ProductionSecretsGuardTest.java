package com.labelhub.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsGuardTest {

    private static SecurityProperties props(String jwtSecret, String internalToken) {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtSecret(jwtSecret);
        properties.setInternalToken(internalToken);
        return properties;
    }

    @Test
    void rejects_dev_default_jwt_secret() {
        SecurityProperties properties = props(
            ProductionSecretsGuard.DEV_DEFAULT_JWT_SECRET, "a-real-production-internal-token");
        assertThatThrownBy(() -> new ProductionSecretsGuard(properties).verify())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejects_missing_jwt_secret() {
        assertThatThrownBy(() -> new ProductionSecretsGuard(props(null, "a-real-production-internal-token")).verify())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void rejects_dev_default_internal_token() {
        SecurityProperties properties = props(
            "a-unique-production-jwt-secret-that-is-32b+", ProductionSecretsGuard.DEV_DEFAULT_INTERNAL_TOKEN);
        assertThatThrownBy(() -> new ProductionSecretsGuard(properties).verify())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LABELHUB_INTERNAL_TOKEN");
    }

    @Test
    void rejects_missing_internal_token() {
        assertThatThrownBy(() -> new ProductionSecretsGuard(props("a-unique-production-jwt-secret-that-is-32b+", "")).verify())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LABELHUB_INTERNAL_TOKEN");
    }

    @Test
    void accepts_real_production_secrets() {
        SecurityProperties properties = props(
            "a-unique-production-jwt-secret-that-is-32b+", "a-real-production-internal-token");
        assertThatCode(() -> new ProductionSecretsGuard(properties).verify()).doesNotThrowAnyException();
    }
}
