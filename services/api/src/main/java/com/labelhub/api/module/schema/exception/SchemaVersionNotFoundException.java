package com.labelhub.api.module.schema.exception;

public class SchemaVersionNotFoundException extends RuntimeException {
    public SchemaVersionNotFoundException(Long versionId) {
        super("Schema version not found: " + versionId);
    }
}
