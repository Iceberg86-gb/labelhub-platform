package com.labelhub.agent.outbox;

import com.labelhub.agent.llm.runtime.RuntimeProviderCallException;
import java.util.Locale;
import java.util.regex.Pattern;

public class OutboxLastErrorBuilder {

    private static final int MAX_LENGTH = 1000;
    private static final int MESSAGE_LIMIT = 500;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("sk-[A-Za-z0-9._-]+");
    private static final Pattern AUTH_PATTERN = Pattern.compile("(?i)(authori" + "zation\\s*[:=]\\s*)\\S+(\\s+\\S+)?");
    private static final Pattern B_TOKEN_PATTERN = Pattern.compile("(?i)bea" + "rer\\s+\\S+");
    private static final Pattern REQUEST_CONTENT_PATTERN = Pattern.compile("(?i)answer[_-]payload\\s*[:=]?\\s*\\S*");

    public String buildLastError(Throwable throwable) {
        if (throwable == null) {
            return "reason=unknown; exception=Unknown; message=unknown";
        }
        String reason = reason(throwable);
        StringBuilder value = new StringBuilder()
            .append("reason=").append(reason)
            .append("; exception=").append(throwable.getClass().getSimpleName());
        if (throwable instanceof RuntimeProviderCallException providerException
            && providerException.getStatusCode() != null) {
            value.append("; status=").append(providerException.getStatusCode());
        }
        String message = safeMessage(throwable.getMessage());
        if (!message.isBlank()) {
            value.append("; message=").append(message);
        }
        if (throwable instanceof RuntimeProviderCallException providerException
            && hasText(providerException.getProviderBodySummary())) {
            value.append("; providerBody=").append(safeMessage(providerException.getProviderBodySummary()));
        }
        return truncate(value.toString(), MAX_LENGTH);
    }

    private String reason(Throwable throwable) {
        if (throwable instanceof RuntimeProviderCallException providerException
            && hasText(providerException.getProviderCode())) {
            return sanitizeReason(providerException.getProviderCode());
        }
        String simpleName = throwable.getClass().getSimpleName();
        if (!hasText(simpleName)) {
            return "unknown";
        }
        String withoutException = simpleName.replaceAll("(?i)Exception$", "");
        String snakeCase = withoutException
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .toLowerCase(Locale.ROOT);
        return hasText(snakeCase) ? snakeCase : "unknown";
    }

    private String safeMessage(String message) {
        if (message == null) {
            return "";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        normalized = TOKEN_PATTERN.matcher(normalized).replaceAll("[redacted]");
        normalized = AUTH_PATTERN.matcher(normalized).replaceAll("[redacted]");
        normalized = B_TOKEN_PATTERN.matcher(normalized).replaceAll("[redacted]");
        normalized = REQUEST_CONTENT_PATTERN.matcher(normalized).replaceAll("[redacted]");
        return truncate(normalized, MESSAGE_LIMIT);
    }

    private String sanitizeReason(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
        return hasText(normalized) ? truncate(normalized, 120) : "unknown";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
