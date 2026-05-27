package com.labelhub.api.module.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.AuditLog;
import com.labelhub.api.generated.model.PagedAuditLogs;
import com.labelhub.api.module.admin.audit.AuditLogFilterCriteria;
import com.labelhub.api.module.admin.entity.AuditLogRow;
import com.labelhub.api.module.admin.exception.PayloadTooLargeException;
import com.labelhub.api.module.admin.mapper.AuditLogQueryMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuditLogQueryService {

    private static final int EXPORT_LIMIT = 50_000;
    private static final int EXPORT_LIMIT_WITH_OVERFLOW_SENTINEL = EXPORT_LIMIT + 1;
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final AuditLogQueryMapper auditLogQueryMapper;
    private final ObjectMapper objectMapper;

    public AuditLogQueryService(AuditLogQueryMapper auditLogQueryMapper, ObjectMapper objectMapper) {
        this.auditLogQueryMapper = auditLogQueryMapper;
        this.objectMapper = objectMapper;
    }

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
        AuditLogFilterCriteria criteria = criteria(page, size, actionTypes, resourceTypes, actorUserId, resourceId, from, to);
        List<AuditLogRow> rows = auditLogQueryMapper.selectFiltered(criteria, offset(criteria));
        Long total = auditLogQueryMapper.countFiltered(criteria);

        PagedAuditLogs response = new PagedAuditLogs();
        response.setItems(rows.stream().map(this::toDto).toList());
        response.setTotal(total == null ? 0L : total);
        response.setPage(criteria.page());
        response.setSize(criteria.size());
        return response;
    }

    public String exportAuditLogsCsv(
        String actionTypes,
        String resourceTypes,
        Long actorUserId,
        Long resourceId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        AuditLogFilterCriteria criteria = criteria(1, EXPORT_LIMIT, actionTypes, resourceTypes, actorUserId, resourceId, from, to);
        List<AuditLogRow> rows = auditLogQueryMapper.streamFiltered(criteria, EXPORT_LIMIT_WITH_OVERFLOW_SENTINEL);
        if (rows.size() > EXPORT_LIMIT) {
            throw new PayloadTooLargeException("Audit log export exceeds 50000 rows; narrow the filters and try again");
        }

        StringBuilder csv = new StringBuilder();
        csv.append("id,timestamp,actor_type,actor_id,actor_display_name,action,resource_type,resource_id,payload_hash,payload_json\n");
        for (AuditLogRow row : rows) {
            csv.append(csv(row.getId())).append(',')
                .append(csv(row.getCreatedAt())).append(',')
                .append(csv(row.getActorType())).append(',')
                .append(csv(row.getActorId())).append(',')
                .append(csv(row.getActorDisplayName())).append(',')
                .append(csv(row.getAction())).append(',')
                .append(csv(row.getResourceType())).append(',')
                .append(csv(row.getResourceId())).append(',')
                .append(csv(row.getPayloadHash())).append(',')
                .append(csv(row.getPayload()))
                .append('\n');
        }
        return csv.toString();
    }

    private AuditLogFilterCriteria criteria(
        Integer page,
        Integer size,
        String actionTypes,
        String resourceTypes,
        Long actorUserId,
        Long resourceId,
        OffsetDateTime from,
        OffsetDateTime to
    ) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        return new AuditLogFilterCriteria(
            csvList(actionTypes),
            csvList(resourceTypes),
            actorUserId,
            resourceId,
            toLocalDateTime(from),
            toLocalDateTime(to),
            safePage,
            safeSize
        );
    }

    private List<String> csvList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .toList();
    }

    private long offset(AuditLogFilterCriteria criteria) {
        return (long) (criteria.page() - 1) * criteria.size();
    }

    private AuditLog toDto(AuditLogRow row) {
        AuditLog dto = new AuditLog();
        dto.setId(row.getId());
        dto.setActorType(AuditLog.ActorTypeEnum.fromValue(row.getActorType()));
        dto.setActorId(row.getActorId());
        dto.setActorDisplayName(row.getActorDisplayName());
        dto.setAction(row.getAction());
        dto.setResourceType(row.getResourceType());
        dto.setResourceId(row.getResourceId());
        dto.setPayload(readPayload(row.getPayload()));
        dto.setPayloadHash(row.getPayloadHash());
        dto.setCreatedAt(offset(row.getCreatedAt()));
        return dto;
    }

    private Map<String, Object> readPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to parse audit payload JSON", exception);
        }
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString();
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }
}
