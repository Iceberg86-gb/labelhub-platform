package com.labelhub.api.module.export.service;

public enum ExportSnapshotPackageType {
    ANNOTATION_RESULTS("annotation_results"),
    TRAINING_DATA("training_data");

    private final String value;

    ExportSnapshotPackageType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ExportSnapshotPackageType fromValue(String value) {
        for (ExportSnapshotPackageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported export package type: " + value);
    }
}
