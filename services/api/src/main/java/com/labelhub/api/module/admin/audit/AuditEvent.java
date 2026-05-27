package com.labelhub.api.module.admin.audit;

import java.util.Map;

public record AuditEvent(
    String actorType,
    Long actorId,
    String action,
    String resourceType,
    Long resourceId,
    Map<String, Object> payload
) {}
