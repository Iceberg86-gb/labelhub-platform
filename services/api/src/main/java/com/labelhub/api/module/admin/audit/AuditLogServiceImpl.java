package com.labelhub.api.module.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.admin.entity.AuditLogEntity;
import com.labelhub.api.module.admin.mapper.AuditLogMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final Canonicalizer canonicalizer;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(
        AuditLogMapper auditLogMapper,
        Canonicalizer canonicalizer,
        ObjectMapper objectMapper
    ) {
        this.auditLogMapper = auditLogMapper;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
    }

    /**
     * Records audit evidence in the caller's transaction. This is fail-fast by design:
     * audit write failure rolls back the business mutation because audit is governance evidence.
     */
    @Override
    public void record(AuditEventBuilder builder) {
        AuditEvent event = builder.build();
        String canonicalPayload = canonicalizer.canonicalJson(event.payload());

        AuditLogEntity entity = new AuditLogEntity();
        entity.setActorType(event.actorType());
        entity.setActorId(event.actorId());
        entity.setAction(event.action());
        entity.setResourceType(event.resourceType());
        entity.setResourceId(event.resourceId());
        entity.setPayload(writeJson(event.payload()));
        entity.setPayloadHash(canonicalizer.sha256Hex(canonicalPayload));

        int inserted = auditLogMapper.insert(entity);
        if (inserted != 1) {
            throw new IllegalStateException("Expected one row for insert audit log but got " + inserted);
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to write audit payload JSON", exception);
        }
    }
}
