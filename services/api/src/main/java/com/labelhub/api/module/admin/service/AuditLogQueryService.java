package com.labelhub.api.module.admin.service;

import com.labelhub.api.generated.model.PagedAuditLogs;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class AuditLogQueryService {

    public PagedAuditLogs listAuditLogs(
        Integer page,
        Integer size,
        String actionTypes,
        String resourceTypes,
        Long actorUserId,
        Long resourceId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        throw new UnsupportedOperationException("M7-P1 Cluster 2 pending: query/export implementation deferred");
    }

    public String exportAuditLogsCsv(
        String actionTypes,
        String resourceTypes,
        Long actorUserId,
        Long resourceId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        throw new UnsupportedOperationException("M7-P1 Cluster 2 pending: query/export implementation deferred");
    }
}
