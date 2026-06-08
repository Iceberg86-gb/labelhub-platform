package com.labelhub.api.module.schema.service;

import java.util.Map;

public record SchemaExportPackageView(
    int packageVersion,
    Long schemaId,
    Long versionId,
    Integer versionNumber,
    String name,
    String description,
    Map<String, Object> schemaJson
) {
}
