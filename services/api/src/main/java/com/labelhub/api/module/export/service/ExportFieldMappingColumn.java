package com.labelhub.api.module.export.service;

public record ExportFieldMappingColumn(String source, String columnName, boolean included) {
    public ExportFieldMappingColumn {
        source = source == null ? "" : source.trim();
        columnName = columnName == null || columnName.isBlank() ? source : columnName.trim();
    }
}
