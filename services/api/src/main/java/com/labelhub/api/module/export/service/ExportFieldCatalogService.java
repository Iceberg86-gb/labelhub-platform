package com.labelhub.api.module.export.service;

import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class ExportFieldCatalogService {

    private static final int MAX_SAMPLE_ROWS = 5;
    private static final int MAX_SAMPLE_VALUES = 3;
    private static final int MAX_DISTINCT_CARDINALITY = 8;
    private static final int CATEGORICAL_MAX_CARDINALITY = 6;

    private static final List<String> SYSTEM_FIELDS = List.of(
        "task_id", "dataset_item_id", "submission_id", "schema_version_id", "submitted_at", "final_verdict", "reviewed_at"
    );
    private static final Map<String, String> SYSTEM_LABELS = Map.of(
        "task_id", "任务 ID",
        "dataset_item_id", "数据项 ID",
        "submission_id", "提交 ID",
        "schema_version_id", "Schema 版本",
        "submitted_at", "提交时间",
        "final_verdict", "终审结论",
        "reviewed_at", "审核时间"
    );

    private final TaskMapper taskMapper;
    private final ExportFactCollector factCollector;
    private final ExportArtifactBuilder artifactBuilder;

    public ExportFieldCatalogService(TaskMapper taskMapper, ExportFactCollector factCollector, ExportArtifactBuilder artifactBuilder) {
        this.taskMapper = taskMapper;
        this.factCollector = factCollector;
        this.artifactBuilder = artifactBuilder;
    }

    public ExportFieldCatalogView buildForOwner(Long taskId, Long ownerUserId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerUserId)) {
            throw new TaskNotFoundException(taskId);
        }
        ExportFactBundle bundle = factCollector.collectForTask(taskId, ExportDataScope.APPROVED_ONLY);
        return build(artifactBuilder.businessRows(bundle), fieldTitles(bundle));
    }

    ExportFieldCatalogView build(List<Map<String, String>> rows) {
        return build(rows, Map.of());
    }

    ExportFieldCatalogView build(List<Map<String, String>> rows, Map<String, String> fieldTitles) {
        List<String> orderedKeys = orderedKeys(rows);
        List<ExportFieldCatalogView.Field> fields = new ArrayList<>();
        for (String key : orderedKeys) {
            fields.add(describe(key, rows, fieldTitles));
        }
        List<Map<String, String>> sampleRows = rows.stream().limit(MAX_SAMPLE_ROWS).map(LinkedHashMap::new).map(m -> (Map<String, String>) m).toList();
        Recommendation recommendation = recommend(fields);
        return new ExportFieldCatalogView(
            rows.size(),
            fields,
            sampleRows,
            recommendation.format(),
            recommendation.reason(),
            recommendation.bindings()
        );
    }

    private List<String> orderedKeys(List<Map<String, String>> rows) {
        Set<String> itemKeys = new TreeSet<>();
        Set<String> answerKeys = new TreeSet<>();
        Set<String> presentSystem = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            for (String key : row.keySet()) {
                if (key.startsWith("item.")) {
                    itemKeys.add(key);
                } else if (key.startsWith("answer.")) {
                    answerKeys.add(key);
                } else {
                    presentSystem.add(key);
                }
            }
        }
        List<String> ordered = new ArrayList<>();
        for (String systemField : SYSTEM_FIELDS) {
            if (presentSystem.contains(systemField)) {
                ordered.add(systemField);
            }
        }
        ordered.addAll(itemKeys);
        ordered.addAll(answerKeys);
        return ordered;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fieldTitles(ExportFactBundle bundle) {
        Map<String, String> titles = new LinkedHashMap<>();
        if (bundle == null || bundle.schemaVersions() == null) {
            return titles;
        }
        for (var schemaVersion : bundle.schemaVersions()) {
            Object schemaJson = schemaVersion.getSchemaJson();
            if (!(schemaJson instanceof Map<?, ?> schemaMap)) {
                continue;
            }
            Object properties = schemaMap.get("properties");
            if (!(properties instanceof Map<?, ?> propertyMap)) {
                continue;
            }
            for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
                String stableId = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> definition && definition.get("title") instanceof String title
                    && !title.isBlank() && !titles.containsKey(stableId)) {
                    titles.put(stableId, title);
                }
            }
        }
        return titles;
    }

    private ExportFieldCatalogView.Field describe(String key, List<Map<String, String>> rows, Map<String, String> fieldTitles) {
        int nonEmpty = 0;
        List<String> sampleValues = new ArrayList<>();
        Set<String> distinct = new LinkedHashSet<>();
        boolean distinctOverflow = false;
        for (Map<String, String> row : rows) {
            String value = row.get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            nonEmpty++;
            if (sampleValues.size() < MAX_SAMPLE_VALUES && !sampleValues.contains(value)) {
                sampleValues.add(value);
            }
            if (!distinctOverflow) {
                distinct.add(value);
                if (distinct.size() > MAX_DISTINCT_CARDINALITY) {
                    distinctOverflow = true;
                }
            }
        }
        double ratio = rows.isEmpty() ? 0.0 : (double) nonEmpty / rows.size();
        List<String> distinctValues = distinctOverflow ? null : List.copyOf(distinct);
        return new ExportFieldCatalogView.Field(key, labelFor(key, fieldTitles), groupFor(key), ratio, sampleValues, distinctValues);
    }

    private String labelFor(String key, Map<String, String> fieldTitles) {
        if (SYSTEM_LABELS.containsKey(key)) {
            return SYSTEM_LABELS.get(key);
        }
        int dot = key.indexOf('.');
        String stableId = dot >= 0 ? key.substring(dot + 1) : key;
        return fieldTitles.getOrDefault(stableId, stableId);
    }

    private boolean isStructuredField(ExportFieldCatalogView.Field field) {
        if (field.sampleValues().isEmpty()) {
            return false;
        }
        return field.sampleValues().stream().allMatch(value -> {
            String trimmed = value.trim();
            return trimmed.startsWith("[") || trimmed.startsWith("{");
        });
    }

    private boolean isCategoricalField(ExportFieldCatalogView.Field field) {
        List<String> distinct = field.distinctValues();
        return distinct != null && !distinct.isEmpty() && distinct.size() <= CATEGORICAL_MAX_CARDINALITY;
    }

    private String groupFor(String key) {
        if (key.startsWith("item.")) {
            return "item";
        }
        if (key.startsWith("answer.")) {
            return "answer";
        }
        return "system";
    }

    private Recommendation recommend(List<ExportFieldCatalogView.Field> fields) {
        List<ExportFieldCatalogView.Field> itemFields = fields.stream().filter(f -> "item".equals(f.group())).toList();
        List<ExportFieldCatalogView.Field> answerFields = fields.stream().filter(f -> "answer".equals(f.group())).toList();

        Map<String, String> pair = detectChoicePair(itemFields);
        if (!pair.isEmpty()) {
            String promptSource = pickPromptSource(itemFields);
            ExportFieldCatalogView.Field preference = detectPreferenceField(answerFields, pair.keySet());
            if (preference != null) {
                return new Recommendation(
                    TrainingExportProfile.Format.TRL_DPO_JSONL.value(),
                    "检测到成对候选回答与偏好字段,建议使用偏好对比训练。请在预览中确认“回答 A/B”绑定的是回答内容,而非模型名等标识。",
                    new ExportFieldCatalogView.RecommendedBindings(promptSource, null, preference.source(), pair)
                );
            }
            return new Recommendation(
                TrainingExportProfile.Format.TRL_DPO_JSONL.value(),
                "检测到成对候选回答,建议使用偏好对比训练,请先选择偏好字段。请在预览中确认“回答 A/B”绑定的是回答内容,而非模型名等标识。",
                new ExportFieldCatalogView.RecommendedBindings(promptSource, null, null, pair)
            );
        }

        String promptSource = pickPromptSource(itemFields);
        String completionSource = pickCompletionSource(answerFields);
        if (promptSource != null && completionSource != null) {
            return new Recommendation(
                TrainingExportProfile.Format.OPENAI_CHAT_SFT_JSONL.value(),
                "未检测到成对候选,建议使用对话或指令微调(提示 → 回答)。请在预览中确认“助手回答”绑定的是真实答案,而非评判或维度等字段。",
                new ExportFieldCatalogView.RecommendedBindings(promptSource, completionSource, null, null)
            );
        }

        return new Recommendation(
            TrainingExportProfile.Format.FLAT_TABLE.value(),
            "可用于训练的字段不足,默认导出表格快照。",
            null
        );
    }

    /**
     * Detect the best paired item field set (item.response_a / item.response_b style), keyed by uppercased
     * suffix (A, B). When several pairs exist (e.g. model_a/model_b names and response_a/response_b texts),
     * prefer the pair that holds actual answer content: bias by name semantics and by sample-value length,
     * so identifier fields like model names are not mistaken for candidate responses.
     */
    private Map<String, String> detectChoicePair(List<ExportFieldCatalogView.Field> itemFields) {
        Map<String, ExportFieldCatalogView.Field> bySource = new LinkedHashMap<>();
        for (ExportFieldCatalogView.Field field : itemFields) {
            bySource.put(field.source(), field);
        }
        Map<String, String> best = null;
        long bestScore = Long.MIN_VALUE;
        for (ExportFieldCatalogView.Field field : itemFields) {
            String source = field.source();
            if (!source.endsWith("_a")) {
                continue;
            }
            String counterpart = source.substring(0, source.length() - 2) + "_b";
            ExportFieldCatalogView.Field other = bySource.get(counterpart);
            if (other == null || isStructuredField(field) || isStructuredField(other)) {
                continue;
            }
            long score = choicePairScore(source, field, other);
            if (score > bestScore) {
                bestScore = score;
                Map<String, String> pair = new LinkedHashMap<>();
                pair.put("A", source);
                pair.put("B", counterpart);
                best = pair;
            }
        }
        return best == null ? Map.of() : best;
    }

    private long choicePairScore(String sourceA, ExportFieldCatalogView.Field fieldA, ExportFieldCatalogView.Field fieldB) {
        String base = sourceA.substring(sourceA.indexOf('.') + 1, sourceA.length() - 2).toLowerCase();
        long score = Math.min(maxSampleLength(fieldA), maxSampleLength(fieldB));
        for (String hint : List.of("response", "answer", "output", "completion", "reply", "candidate", "text")) {
            if (base.contains(hint)) {
                score += 1_000_000L;
                break;
            }
        }
        for (String identifier : List.of("model", "name", "id", "label", "tag", "source")) {
            if (base.contains(identifier)) {
                score -= 1_000_000L;
                break;
            }
        }
        return score;
    }

    private int maxSampleLength(ExportFieldCatalogView.Field field) {
        return field.sampleValues().stream().mapToInt(String::length).max().orElse(0);
    }

    private ExportFieldCatalogView.Field detectPreferenceField(List<ExportFieldCatalogView.Field> answerFields, Set<String> choiceKeys) {
        for (ExportFieldCatalogView.Field field : answerFields) {
            List<String> distinct = field.distinctValues();
            if (distinct == null || distinct.isEmpty()) {
                continue;
            }
            boolean allMatch = distinct.stream().allMatch(value -> choiceKeys.contains(value.trim().toUpperCase()));
            if (allMatch) {
                return field;
            }
        }
        return null;
    }

    private String pickPromptSource(List<ExportFieldCatalogView.Field> itemFields) {
        List<ExportFieldCatalogView.Field> textFields = itemFields.stream().filter(f -> !isStructuredField(f)).toList();
        for (String hint : List.of("prompt", "question", "input", "instruction")) {
            for (ExportFieldCatalogView.Field field : textFields) {
                if (field.source().toLowerCase().contains(hint)) {
                    return field.source();
                }
            }
        }
        if (!textFields.isEmpty()) {
            return textFields.get(0).source();
        }
        return itemFields.isEmpty() ? null : itemFields.get(0).source();
    }

    /**
     * Pick the completion field for SFT: a free-text answer field, skipping structured (array/object)
     * values and low-cardinality categorical fields (labels, verdicts, flags), preferring the longest text.
     */
    private String pickCompletionSource(List<ExportFieldCatalogView.Field> answerFields) {
        List<ExportFieldCatalogView.Field> textFields = answerFields.stream().filter(f -> !isStructuredField(f)).toList();
        String freeText = longestSample(textFields.stream().filter(f -> !isCategoricalField(f)).toList());
        if (freeText != null) {
            return freeText;
        }
        return longestSample(textFields);
    }

    private String longestSample(List<ExportFieldCatalogView.Field> candidates) {
        ExportFieldCatalogView.Field best = null;
        int bestLength = -1;
        for (ExportFieldCatalogView.Field field : candidates) {
            int length = field.sampleValues().stream().mapToInt(String::length).max().orElse(0);
            if (length > bestLength) {
                bestLength = length;
                best = field;
            }
        }
        return best == null ? null : best.source();
    }

    private record Recommendation(String format, String reason, ExportFieldCatalogView.RecommendedBindings bindings) {
    }
}
