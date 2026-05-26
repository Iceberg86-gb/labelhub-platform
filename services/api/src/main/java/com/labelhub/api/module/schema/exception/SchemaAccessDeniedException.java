package com.labelhub.api.module.schema.exception;

public class SchemaAccessDeniedException extends RuntimeException {
    public SchemaAccessDeniedException(Long schemaId, Long ownerId) {
        super("Owner " + ownerId + " has no access to schema " + schemaId);
    }
}
