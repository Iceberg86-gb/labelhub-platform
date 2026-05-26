package com.labelhub.api.module.schema.exception;

public class SchemaNotFoundException extends RuntimeException {
    public SchemaNotFoundException(Long schemaId) {
        super("Schema not found: " + schemaId);
    }
}
