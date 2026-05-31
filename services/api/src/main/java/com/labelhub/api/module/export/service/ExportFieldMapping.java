package com.labelhub.api.module.export.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ExportFieldMapping(List<ExportFieldMappingColumn> columns) {
    private static final String VERSION = "p-f-field-mapping-v1";

    public static ExportFieldMapping empty() {
        return new ExportFieldMapping(List.of());
    }

    public ExportFieldMapping {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    List<ExportFieldMappingColumn> effectiveColumns(List<String> availableSources) {
        if (columns.isEmpty()) {
            return availableSources.stream()
                .map(source -> new ExportFieldMappingColumn(source, source, true))
                .toList();
        }
        Set<String> available = new LinkedHashSet<>(availableSources);
        Set<String> seenSources = new LinkedHashSet<>();
        List<ExportFieldMappingColumn> effective = new ArrayList<>();
        for (ExportFieldMappingColumn column : columns) {
            if (!column.included() || !available.contains(column.source()) || !seenSources.add(column.source())) {
                continue;
            }
            effective.add(column);
        }
        return effective;
    }

    Map<String, Object> snapshot(List<String> availableSources) {
        List<Map<String, Object>> mappedColumns = effectiveColumns(availableSources).stream()
            .map(column -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("source", column.source());
                entry.put("columnName", column.columnName());
                entry.put("included", column.included());
                return entry;
            })
            .toList();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", VERSION);
        snapshot.put("columns", mappedColumns);
        return snapshot;
    }
}
