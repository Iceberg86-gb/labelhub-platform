package com.labelhub.api.module.admin.web;

import com.labelhub.api.generated.model.PagedAuditLogs;
import com.labelhub.api.generated.web.AuditLogsApi;
import com.labelhub.api.module.admin.service.AuditLogQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditLogsController implements AuditLogsApi {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogsController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/audit-logs/export.csv", produces = "text/csv; charset=utf-8")
    public ResponseEntity<String> exportAuditLogs(
        @Valid @RequestParam(value = "actionTypes", required = false) String actionTypes,
        @Valid @RequestParam(value = "resourceTypes", required = false) String resourceTypes,
        @Valid @RequestParam(value = "actorUserId", required = false) Long actorUserId,
        @Valid @RequestParam(value = "resourceId", required = false) Long resourceId,
        @Valid @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @Valid @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(auditLogQueryService.exportAuditLogsCsv(
            actionTypes,
            resourceTypes,
            actorUserId,
            resourceId,
            from,
            to
        ));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/audit-logs", produces = "application/json")
    public ResponseEntity<PagedAuditLogs> listAuditLogs(
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
        @Valid @RequestParam(value = "actionTypes", required = false) String actionTypes,
        @Valid @RequestParam(value = "resourceTypes", required = false) String resourceTypes,
        @Valid @RequestParam(value = "actorUserId", required = false) Long actorUserId,
        @Valid @RequestParam(value = "resourceId", required = false) Long resourceId,
        @Valid @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @Valid @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(auditLogQueryService.listAuditLogs(
            page,
            size,
            actionTypes,
            resourceTypes,
            actorUserId,
            resourceId,
            from,
            to
        ));
    }
}
