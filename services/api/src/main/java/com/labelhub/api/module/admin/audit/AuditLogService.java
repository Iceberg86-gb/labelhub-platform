package com.labelhub.api.module.admin.audit;

public interface AuditLogService {
    void record(AuditEventBuilder builder);
}
