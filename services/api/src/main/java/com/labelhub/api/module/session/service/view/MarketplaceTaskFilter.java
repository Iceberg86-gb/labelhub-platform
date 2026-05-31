package com.labelhub.api.module.session.service.view;

import java.util.Locale;
import java.util.Set;

public record MarketplaceTaskFilter(String query, String tag, Boolean hasReward, String deadline) {

    private static final Set<String> DEADLINE_FILTERS = Set.of("day", "week");

    public MarketplaceTaskFilter {
        query = blankToNull(query);
        tag = blankToNull(tag);
        hasReward = Boolean.TRUE.equals(hasReward) ? Boolean.TRUE : null;
        deadline = normalizeDeadline(deadline);
    }

    public static MarketplaceTaskFilter empty() {
        return new MarketplaceTaskFilter(null, null, null, null);
    }

    public Integer deadlineDays() {
        if ("day".equals(deadline)) {
            return 1;
        }
        if ("week".equals(deadline)) {
            return 7;
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeDeadline(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return DEADLINE_FILTERS.contains(normalized) ? normalized : null;
    }
}
