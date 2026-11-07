package com.labelhub.api.module.ai.precedent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ADR-021 §3: deployment-level precedent memory switch ({@code labelhub.ai.precedents.*}). Off by default;
 * turning it on changes the provider adapter version, so idempotency keys and ADR-020 shadow evidence of
 * calls with and without precedents never mix.
 */
@Component
@ConfigurationProperties(prefix = "labelhub.ai.precedents")
public class PrecedentProperties {

    public static final String BASE_ADAPTER_VERSION = "agent-default-v1";

    private boolean enabled = false;
    private int topK = 3;
    private int poolLimit = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getPoolLimit() {
        return poolLimit;
    }

    public void setPoolLimit(int poolLimit) {
        this.poolLimit = poolLimit;
    }

    /** Adapter version of review calls made under the current setting. */
    public String adapterVersion() {
        return adapterVersion(enabled);
    }

    public static String adapterVersion(boolean withPrecedents) {
        return withPrecedents ? BASE_ADAPTER_VERSION + "+" + PrecedentBlock.VERSION : BASE_ADAPTER_VERSION;
    }
}
