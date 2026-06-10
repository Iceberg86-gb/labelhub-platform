package com.labelhub.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the application while security secrets still hold the public development
 * defaults committed in application.yml (the {@code ${ENV:dev-default}} fallbacks). It loads ONLY
 * under the {@code prod} profile — wired via {@code SPRING_PROFILES_ACTIVE=prod} in
 * {@code infra/docker-compose.prod.yml} — so local/demo/test, which run the default profile with
 * those dev defaults, are unaffected.
 *
 * <p>This closes the fail-open hole where an operator who forgets to set {@code JWT_SECRET} /
 * {@code LABELHUB_INTERNAL_TOKEN} would otherwise boot production with a secret that is published
 * in this repository (forgeable tokens / guessable internal-API access).
 */
@Component
@Profile("prod")
public class ProductionSecretsGuard {

    // Must byte-for-byte mirror the dev defaults in application.yml (jwt-secret / internal-token).
    static final String DEV_DEFAULT_JWT_SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";
    static final String DEV_DEFAULT_INTERNAL_TOKEN = "dev-internal-token";

    private final SecurityProperties properties;

    public ProductionSecretsGuard(SecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        if (isMissingOrDevDefault(properties.getJwtSecret(), DEV_DEFAULT_JWT_SECRET)) {
            throw new IllegalStateException(
                "JWT_SECRET is unset or still the development default in the prod profile. "
                    + "Set a unique JWT_SECRET (e.g. `openssl rand -base64 32`) before starting production.");
        }
        if (isMissingOrDevDefault(properties.getInternalToken(), DEV_DEFAULT_INTERNAL_TOKEN)) {
            throw new IllegalStateException(
                "LABELHUB_INTERNAL_TOKEN is unset or still the development default in the prod profile. "
                    + "Set a unique LABELHUB_INTERNAL_TOKEN before starting production.");
        }
    }

    private static boolean isMissingOrDevDefault(String value, String devDefault) {
        return value == null || value.isBlank() || devDefault.equals(value);
    }
}
