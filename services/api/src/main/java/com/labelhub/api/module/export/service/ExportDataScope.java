package com.labelhub.api.module.export.service;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ExportDataScope {
    APPROVED_ONLY("approved_only", "approved_only"),
    FULL("full", "task_complete"),
    /** ADR-020 §3: human-approved plus auto-pass-policy-admitted submissions. Opt-in; never the default. */
    APPROVED_WITH_POLICY("approved_with_policy", "approved_with_policy");

    private final String mode;
    private final String type;

    ExportDataScope(String mode, String type) {
        this.mode = mode;
        this.type = type;
    }

    public String mode() {
        return mode;
    }

    public Map<String, Object> toSnapshotDataScope() {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("type", type);
        scope.put("mode", mode);
        if (this == APPROVED_ONLY) {
            scope.put("verdict", "approved");
        }
        if (this == APPROVED_WITH_POLICY) {
            scope.put("verdict", "approved");
            scope.put("includePolicyAccepted", true);
        }
        return scope;
    }

    public static ExportDataScope fromMode(String mode) {
        if (mode == null || mode.isBlank() || APPROVED_ONLY.mode.equals(mode)) {
            return APPROVED_ONLY;
        }
        if (FULL.mode.equals(mode)) {
            return FULL;
        }
        if (APPROVED_WITH_POLICY.mode.equals(mode)) {
            return APPROVED_WITH_POLICY;
        }
        throw new IllegalArgumentException("Unsupported export mode: " + mode);
    }
}
