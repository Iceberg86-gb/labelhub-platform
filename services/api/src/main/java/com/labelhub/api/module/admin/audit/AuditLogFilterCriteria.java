package com.labelhub.api.module.admin.audit;

import java.time.LocalDateTime;
import java.util.List;

public record AuditLogFilterCriteria(
    List<String> actionTypes,
    List<String> resourceTypes,
    Long actorUserId,
    Long resourceId,
    LocalDateTime from,
    LocalDateTime to,
    int page,
    int size
) {}
