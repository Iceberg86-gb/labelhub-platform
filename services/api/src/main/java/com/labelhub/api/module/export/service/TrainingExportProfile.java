package com.labelhub.api.module.export.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TrainingExportProfile(
    Format format,
    String promptSource,
    String completionSource,
    String preferenceSource,
    Map<String, String> choiceSources
) {
    public enum Format {
        FLAT_TABLE("flat_table", null, null),
        OPENAI_CHAT_SFT_JSONL("openai_chat_sft_jsonl", "openai-chat-sft.jsonl", "openaiChatSft"),
        TRL_SFT_JSONL("trl_sft_jsonl", "trl-sft.jsonl", "trlSft"),
        TRL_DPO_JSONL("trl_dpo_jsonl", "trl-dpo.jsonl", "trlDpo");

        private final String value;
        private final String fileName;
        private final String recordCountKey;

        Format(String value, String fileName, String recordCountKey) {
            this.value = value;
            this.fileName = fileName;
            this.recordCountKey = recordCountKey;
        }

        public String value() {
            return value;
        }

        public String fileName() {
            return fileName;
        }

        public String recordCountKey() {
            return recordCountKey;
        }

        public static Format fromValue(String value) {
            if (value == null || value.isBlank()) {
                return FLAT_TABLE;
            }
            for (Format format : values()) {
                if (format.value.equals(value)) {
                    return format;
                }
            }
            throw new IllegalArgumentException("Unsupported training export format: " + value);
        }
    }

    public TrainingExportProfile {
        format = format == null ? Format.FLAT_TABLE : format;
        choiceSources = choiceSources == null ? Map.of() : Map.copyOf(choiceSources);
    }

    public static TrainingExportProfile flatTable() {
        return new TrainingExportProfile(Format.FLAT_TABLE, null, null, null, Map.of());
    }

    public static TrainingExportProfile openAiChatSft(String promptSource, String completionSource) {
        return new TrainingExportProfile(Format.OPENAI_CHAT_SFT_JSONL, promptSource, completionSource, null, Map.of());
    }

    public static TrainingExportProfile trlSft(String promptSource, String completionSource) {
        return new TrainingExportProfile(Format.TRL_SFT_JSONL, promptSource, completionSource, null, Map.of());
    }

    public static TrainingExportProfile trlDpo(String promptSource, String preferenceSource, Map<String, String> choiceSources) {
        return new TrainingExportProfile(Format.TRL_DPO_JSONL, promptSource, null, preferenceSource, choiceSources);
    }

    @SuppressWarnings("unchecked")
    public static TrainingExportProfile fromParameter(Object rawParameter) {
        if (!(rawParameter instanceof Map<?, ?> rawMap)) {
            return flatTable();
        }
        Map<String, Object> parameter = (Map<String, Object>) rawMap;
        Format format = Format.fromValue(stringValue(parameter.get("format")));
        return switch (format) {
            case OPENAI_CHAT_SFT_JSONL -> openAiChatSft(
                stringValue(parameter.get("promptSource")),
                stringValue(parameter.get("completionSource"))
            );
            case TRL_SFT_JSONL -> trlSft(
                stringValue(parameter.get("promptSource")),
                stringValue(parameter.get("completionSource"))
            );
            case TRL_DPO_JSONL -> trlDpo(
                stringValue(parameter.get("promptSource")),
                stringValue(parameter.get("preferenceSource")),
                stringMap(parameter.get("choiceSources"))
            );
            case FLAT_TABLE -> flatTable();
        };
    }

    public boolean enabled() {
        return format != Format.FLAT_TABLE;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", "training-export-profile-v1");
        snapshot.put("format", format.value());
        putIfPresent(snapshot, "promptSource", promptSource);
        putIfPresent(snapshot, "completionSource", completionSource);
        putIfPresent(snapshot, "preferenceSource", preferenceSource);
        if (!choiceSources.isEmpty()) {
            snapshot.put("choiceSources", orderedChoiceSources());
        }
        return snapshot;
    }

    private Map<String, String> orderedChoiceSources() {
        Map<String, String> ordered = new LinkedHashMap<>();
        choiceSources.keySet().stream().sorted().forEach(key -> ordered.put(key, choiceSources.get(key)));
        return ordered;
    }

    private void putIfPresent(Map<String, Object> snapshot, String key, String value) {
        if (value != null && !value.isBlank()) {
            snapshot.put(key, value);
        }
    }

    List<Map.Entry<String, String>> orderedChoices() {
        return orderedChoiceSources().entrySet().stream().toList();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> {
            if (key != null && mapValue != null) {
                result.put(String.valueOf(key), String.valueOf(mapValue));
            }
        });
        return result;
    }
}
