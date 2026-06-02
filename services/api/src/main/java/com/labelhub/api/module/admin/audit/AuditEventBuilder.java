package com.labelhub.api.module.admin.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

public final class AuditEventBuilder {

    private String actorType;
    private Long actorId;
    private String action;
    private String resourceType;
    private Long resourceId;
    private final Map<String, Object> payload = new LinkedHashMap<>();

    private AuditEventBuilder() {}

    public static AuditEventBuilder forAction(String action) {
        AuditEventBuilder builder = new AuditEventBuilder();
        builder.action = action;
        return builder;
    }

    public AuditEventBuilder actorType(String actorType) {
        this.actorType = actorType;
        return this;
    }

    public AuditEventBuilder actorId(Long actorId) {
        this.actorId = actorId;
        return this;
    }

    public AuditEventBuilder actorUser(Long userId) {
        this.actorType = "user";
        this.actorId = userId;
        return this;
    }

    public AuditEventBuilder actorSystem() {
        this.actorType = "system";
        this.actorId = null;
        return this;
    }

    public AuditEventBuilder actorAi() {
        this.actorType = "ai";
        this.actorId = null;
        return this;
    }

    public AuditEventBuilder actorPlatformAdmin(Long userId) {
        this.actorType = "platform_admin";
        this.actorId = userId;
        return this;
    }

    public AuditEventBuilder resource(String resourceType, Long resourceId) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        return this;
    }

    public AuditEventBuilder payload(Map<String, Object> payload) {
        this.payload.clear();
        if (payload != null) {
            this.payload.putAll(payload);
        }
        return this;
    }

    public AuditEventBuilder payload(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }

    public AuditEvent build() {
        return new AuditEvent(
            Objects.requireNonNull(actorType, "actorType is required"),
            actorId,
            Objects.requireNonNull(action, "action is required"),
            Objects.requireNonNull(resourceType, "resourceType is required"),
            resourceId,
            Collections.unmodifiableMap(new LinkedHashMap<>(payload))
        );
    }
}
