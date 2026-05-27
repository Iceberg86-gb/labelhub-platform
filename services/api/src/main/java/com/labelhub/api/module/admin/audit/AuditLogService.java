package com.labelhub.api.module.admin.audit;

public interface AuditLogService {
    void record(AuditEventBuilder builder);

    void recordRequiresNew(AuditEventBuilder builder);

    static AuditLogService noop() {
        return new AuditLogService() {
            @Override
            public void record(AuditEventBuilder builder) {}

            @Override
            public void recordRequiresNew(AuditEventBuilder builder) {}
        };
    }
}
