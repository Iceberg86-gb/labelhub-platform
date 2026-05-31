package com.labelhub.api.module.export.service;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ExportDataScope {
    APPROVED_ONLY("approved_only", "approved_only"),
    FULL("full", "task_complete");

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
        return scope;
    }

    public static ExportDataScope fromMode(String mode) {
        if (mode == null || mode.isBlank() || APPROVED_ONLY.mode.equals(mode)) {
            return APPROVED_ONLY;
        }
        if (FULL.mode.equals(mode)) {
            return FULL;
        }
        throw new IllegalArgumentException("Unsupported export mode: " + mode);
    }
}
