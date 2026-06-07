package com.labelhub.api.module.schema.util;

import com.labelhub.api.generated.model.SchemaFieldType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;

public final class SchemaCustomValidationFunctions {
    public static final String NON_BLANK_TRIMMED = "nonBlankTrimmed";
    public static final String HTTPS_URL = "httpsUrl";
    public static final String JSON_OBJECT = "jsonObject";

    private static final Set<String> SUPPORTED = Set.of(NON_BLANK_TRIMMED, HTTPS_URL, JSON_OBJECT);
    private static final Set<SchemaFieldType> STRING_TYPES = Set.of(
            SchemaFieldType.TEXT,
            SchemaFieldType.TEXTAREA,
            SchemaFieldType.RICH_TEXT,
            SchemaFieldType.DATE);
    private static final Set<SchemaFieldType> OBJECT_TYPES = Set.of(
            SchemaFieldType.JSON_EDITOR,
            SchemaFieldType.LLM_INTERACTION);

    private SchemaCustomValidationFunctions() {}

    public static boolean isSupported(String name) {
        return name != null && SUPPORTED.contains(name);
    }

    public static boolean isCompatible(SchemaFieldType type, String name) {
        if (JSON_OBJECT.equals(name)) {
            return OBJECT_TYPES.contains(type);
        }
        if (HTTPS_URL.equals(name)) {
            return type == SchemaFieldType.TEXT || type == SchemaFieldType.TEXTAREA;
        }
        if (NON_BLANK_TRIMMED.equals(name)) {
            return STRING_TYPES.contains(type);
        }
        return false;
    }

    public static String validate(SchemaFieldType type, String name, Object value) {
        if (!isSupported(name) || !isCompatible(type, name)) {
            return null;
        }
        return switch (name) {
            case JSON_OBJECT -> value instanceof Map<?, ?> ? null : "必须是 JSON 对象";
            case HTTPS_URL -> isHttpsUrl(value) ? null : "必须是 HTTPS URL";
            case NON_BLANK_TRIMMED -> value instanceof String text && !text.trim().isEmpty() ? null : "内容不能为空白";
            default -> null;
        };
    }

    private static boolean isHttpsUrl(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return false;
        }
        try {
            return "https".equalsIgnoreCase(new URI(text).getScheme());
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
