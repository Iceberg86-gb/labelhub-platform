package com.labelhub.api.module.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * ADR-019 (ADR-017 H2): deterministic validation of a candidate {@code record_ai_review} output.
 *
 * <p>Behaviour is pinned by {@code packages/contracts/fixtures/review-output-validation-corpus.json}; the
 * offline evals mirror ({@code evals/labelhub_evals/review_validation.py}) runs the same corpus. Messages are
 * fed back to the model verbatim, so keep them identical to the mirror.
 */
@Component
public class AiReviewOutputValidator {

    public static final String RULES_VERSION = "review-output-v1";
    private static final List<String> REQUIRED = List.of("overallSuggestion", "confidence", "summary", "dimensionScores", "fieldFindings");
    private static final Set<String> SUGGESTIONS = Set.of("pass", "reject", "manual_review");
    private static final Set<String> SEVERITIES = Set.of("info", "warning", "error");

    public record Context(List<String> dimensions, BigDecimal passThreshold, BigDecimal rejectThreshold, Set<String> stableIds) {
    }

    public record Error(String code, String path, String message) {
    }

    public List<Error> validate(Object output, Context context) {
        List<Error> errors = structure(output);
        if (!errors.isEmpty()) {
            return errors;
        }
        Map<?, ?> map = (Map<?, ?>) output;
        List<String> dimensions = context.dimensions() == null || context.dimensions().isEmpty() ? List.of("overall") : context.dimensions();

        Set<String> seen = new HashSet<>();
        List<?> rows = (List<?>) map.get("dimensionScores");
        for (int i = 0; i < rows.size(); i++) {
            String name = (String) ((Map<?, ?>) rows.get(i)).get("dimension");
            String path = "dimensionScores[" + i + "].dimension";
            if (!seen.add(name)) {
                errors.add(new Error("duplicate_dimension", path, "dimension " + name + " is scored more than once"));
                continue;
            }
            if (!dimensions.contains(name)) {
                errors.add(new Error("unknown_dimension", path, "dimension " + name + " is not configured; use only: " + String.join(", ", dimensions)));
            }
        }
        for (String name : dimensions) {
            if (!seen.contains(name)) {
                errors.add(new Error("missing_dimension", "dimensionScores", "configured dimension " + name + " has no score"));
            }
        }

        Set<String> stableIds = context.stableIds() == null ? Set.of() : context.stableIds();
        String known = stableIds.isEmpty() ? "(none)" : String.join(", ", new TreeSet<>(stableIds));
        List<?> findings = (List<?>) map.get("fieldFindings");
        for (int i = 0; i < findings.size(); i++) {
            Map<?, ?> finding = (Map<?, ?>) findings.get(i);
            Object stableId = finding.get("stableId");
            if (stableId != null && !stableIds.contains((String) stableId)) {
                errors.add(new Error("unknown_field", "fieldFindings[" + i + "].stableId", "stableId " + stableId + " does not exist; known fields: " + known));
            }
            String fieldPath = (String) finding.get("fieldPath");
            if (!stableIds.contains(root(fieldPath))) {
                errors.add(new Error("unknown_field", "fieldFindings[" + i + "].fieldPath", "fieldPath " + fieldPath + " does not start with a known field; known fields: " + known));
            }
        }

        String suggestion = (String) map.get("overallSuggestion");
        BigDecimal score = finalScore(map, dimensions);
        String expected = zone(score, context);
        if (!suggestion.equals(expected)) {
            errors.add(new Error(
                "suggestion_score_conflict",
                "overallSuggestion",
                "overallSuggestion " + suggestion + " contradicts the scores: final score " + score.toPlainString() + " routes to " + expected
                    + " (pass >= " + context.passThreshold().toPlainString() + ", reject <= " + context.rejectThreshold().toPlainString()
                    + "); change the suggestion or the scores so they agree"
            ));
        }
        if ("pass".equals(suggestion)) {
            for (int i = 0; i < findings.size(); i++) {
                if ("error".equals(((Map<?, ?>) findings.get(i)).get("severity"))) {
                    errors.add(new Error("error_finding_on_pass", "fieldFindings[" + i + "].severity", "a pass suggestion cannot carry an error-severity finding"));
                }
            }
        }
        return errors;
    }

    /** Same arithmetic as {@link AiReviewScoringPolicy}: first score per dimension, confidence fallback, clamp, HALF_UP to 4 dp. */
    static BigDecimal finalScore(Map<?, ?> output, List<String> dimensions) {
        Map<String, BigDecimal> provided = new LinkedHashMap<>();
        for (Object row : (List<?>) output.get("dimensionScores")) {
            Map<?, ?> map = (Map<?, ?>) row;
            provided.putIfAbsent((String) map.get("dimension"), clamp(decimal(map.get("score"))));
        }
        BigDecimal fallback = clamp(decimal(output.get("confidence")));
        BigDecimal sum = BigDecimal.ZERO;
        for (String dimension : dimensions) {
            sum = sum.add(provided.getOrDefault(dimension, fallback));
        }
        return sum.divide(new BigDecimal(dimensions.size()), 4, RoundingMode.HALF_UP);
    }

    private static String zone(BigDecimal score, Context context) {
        if (score.compareTo(context.passThreshold()) >= 0) {
            return "pass";
        }
        if (score.compareTo(context.rejectThreshold()) <= 0) {
            return "reject";
        }
        return "manual_review";
    }

    private static List<Error> structure(Object output) {
        List<Error> errors = new ArrayList<>();
        if (!(output instanceof Map<?, ?> map)) {
            errors.add(invalid("$", "record_ai_review arguments must be a JSON object"));
            return errors;
        }
        for (String key : REQUIRED) {
            if (!map.containsKey(key)) {
                errors.add(invalid(key, "missing required field " + key));
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        if (!(map.get("overallSuggestion") instanceof String s) || !SUGGESTIONS.contains(s)) {
            errors.add(invalid("overallSuggestion", "overallSuggestion must be one of pass, reject, manual_review"));
        }
        if (!unit(map.get("confidence"))) {
            errors.add(invalid("confidence", "confidence must be a number in [0, 1]"));
        }
        if (!(map.get("summary") instanceof String)) {
            errors.add(invalid("summary", "summary must be a string"));
        }
        if (!(map.get("dimensionScores") instanceof List<?> dims)) {
            errors.add(invalid("dimensionScores", "dimensionScores must be an array"));
        } else {
            for (int i = 0; i < dims.size(); i++) {
                if (!(dims.get(i) instanceof Map<?, ?> row)) {
                    errors.add(invalid("dimensionScores[" + i + "]", "dimension score must be an object"));
                    continue;
                }
                if (!nonBlank(row.get("dimension"))) {
                    errors.add(invalid("dimensionScores[" + i + "].dimension", "dimension must be a non-empty string"));
                }
                if (!unit(row.get("score"))) {
                    errors.add(invalid("dimensionScores[" + i + "].score", "score must be a number in [0, 1]"));
                }
            }
        }
        if (!(map.get("fieldFindings") instanceof List<?> findings)) {
            errors.add(invalid("fieldFindings", "fieldFindings must be an array"));
        } else {
            for (int i = 0; i < findings.size(); i++) {
                String base = "fieldFindings[" + i + "]";
                if (!(findings.get(i) instanceof Map<?, ?> row)) {
                    errors.add(invalid(base, "field finding must be an object"));
                    continue;
                }
                if (!nonBlank(row.get("fieldPath"))) {
                    errors.add(invalid(base + ".fieldPath", "fieldPath must be a non-empty string"));
                }
                if (!(row.get("severity") instanceof String severity) || !SEVERITIES.contains(severity)) {
                    errors.add(invalid(base + ".severity", "severity must be one of info, warning, error"));
                }
                if (!(row.get("finding") instanceof String)) {
                    errors.add(invalid(base + ".finding", "finding must be a string"));
                }
                if (row.get("stableId") != null && !(row.get("stableId") instanceof String)) {
                    errors.add(invalid(base + ".stableId", "stableId must be a string or null"));
                }
                if (row.get("confidence") != null && !unit(row.get("confidence"))) {
                    errors.add(invalid(base + ".confidence", "confidence must be a number in [0, 1] or null"));
                }
            }
        }
        return errors;
    }

    private static Error invalid(String path, String message) {
        return new Error("invalid_structure", path, message);
    }

    private static boolean unit(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        BigDecimal decimal = decimal(value);
        return decimal.compareTo(BigDecimal.ZERO) >= 0 && decimal.compareTo(BigDecimal.ONE) <= 0;
    }

    private static boolean nonBlank(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static BigDecimal clamp(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private static String root(String fieldPath) {
        int cut = fieldPath.length();
        int dot = fieldPath.indexOf('.');
        int bracket = fieldPath.indexOf('[');
        if (dot >= 0) {
            cut = Math.min(cut, dot);
        }
        if (bracket >= 0) {
            cut = Math.min(cut, bracket);
        }
        return fieldPath.substring(0, cut);
    }
}
