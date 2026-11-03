package com.labelhub.api.module.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** ADR-020 §3: Wilson score interval, the bound the auto-pass gate and circuit breaker compare against. */
public final class WilsonInterval {

    static final double Z95 = 1.959963984540054;

    private WilsonInterval() {
    }

    /**
     * Lower 95% bound; the ADR-020 circuit breaker trips when this exceeds max_error, i.e. only on statistically
     * significant evidence that the sampled error is above the cap (the upper bound of a tiny sample is always huge).
     */
    public static BigDecimal lower95(long k, long n) {
        if (n <= 0) {
            return null;
        }
        if (k < 0 || k > n) {
            throw new IllegalArgumentException("k must be in [0, n]");
        }
        double p = (double) k / n;
        double z2 = Z95 * Z95;
        double lower = Math.max(0.0, (p + z2 / (2 * n) - Z95 * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n))) / (1 + z2 / n));
        return BigDecimal.valueOf(lower).setScale(6, RoundingMode.HALF_UP);
    }

    /** Upper 95% bound of a binomial proportion k/n, 6 decimals HALF_UP (conventions §7.2); null when n == 0. */
    public static BigDecimal upper95(long k, long n) {
        if (n <= 0) {
            return null;
        }
        if (k < 0 || k > n) {
            throw new IllegalArgumentException("k must be in [0, n]");
        }
        double p = (double) k / n;
        double z2 = Z95 * Z95;
        double denominator = 1 + z2 / n;
        double centre = p + z2 / (2 * n);
        double margin = Z95 * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n));
        double upper = Math.min(1.0, (centre + margin) / denominator);
        return BigDecimal.valueOf(upper).setScale(6, RoundingMode.HALF_UP);
    }
}
