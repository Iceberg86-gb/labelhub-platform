package com.labelhub.api.module.export.service;

import java.util.List;
import java.util.Map;

/**
 * Data-driven catalog of bindable export source fields used to power the guided export builder.
 */
public record ExportFieldCatalogView(
    int submissionCount,
    List<Field> fields,
    List<Map<String, String>> sampleRows,
    String recommendedFormat,
    String recommendationReason,
    RecommendedBindings recommendedBindings
) {
    public record Field(
        String source,
        String label,
        String group,
        double nonEmptyRatio,
        List<String> sampleValues,
        List<String> distinctValues
    ) {
    }

    public record RecommendedBindings(
        String promptSource,
        String completionSource,
        String preferenceSource,
        Map<String, String> choiceSources
    ) {
    }
}
