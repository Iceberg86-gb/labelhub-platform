package com.labelhub.api.module.export.exception;

public class ExportSnapshotNotFoundException extends RuntimeException {

    public ExportSnapshotNotFoundException(Long snapshotId) {
        super("Export snapshot not found: " + snapshotId);
    }
}
