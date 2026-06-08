package com.labelhub.api.module.schema.exception;

public class SchemaArchiveNotAllowedException extends RuntimeException {

    public SchemaArchiveNotAllowedException(Long schemaId) {
        super("Only library schema templates can be archived: schemaId=" + schemaId);
    }
}
