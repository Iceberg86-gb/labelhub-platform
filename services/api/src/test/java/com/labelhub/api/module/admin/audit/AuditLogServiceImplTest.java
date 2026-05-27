package com.labelhub.api.module.admin.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.admin.entity.AuditLogEntity;
import com.labelhub.api.module.admin.mapper.AuditLogMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceImplTest {

    private final AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Canonicalizer canonicalizer = new Canonicalizer(objectMapper);
    private final AuditLogServiceImpl service = new AuditLogServiceImpl(auditLogMapper, canonicalizer, objectMapper);

    @Test
    void record_insertsAuditLogWithCanonicalPayloadHash() throws Exception {
        when(auditLogMapper.insert(any())).thenReturn(1);

        service.record(AuditEventBuilder.forAction(AuditActions.TASK_DELETE)
            .actorUser(1001L)
            .resource("task", 100L)
            .payload("taskId", 100L));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLogEntity entity = captor.getValue();
        Map<String, Object> payload = objectMapper.readValue(entity.getPayload(), new TypeReference<>() {});
        assertThat(entity.getActorType()).isEqualTo("user");
        assertThat(entity.getAction()).isEqualTo(AuditActions.TASK_DELETE);
        assertThat(entity.getResourceType()).isEqualTo("task");
        assertThat(entity.getPayloadHash()).isEqualTo(canonicalizer.sha256Hex(canonicalizer.canonicalJson(payload)));
    }

    @Test
    void record_throwsWhenInsertDoesNotAffectOneRow() {
        when(auditLogMapper.insert(any())).thenReturn(0);

        assertThatThrownBy(() -> service.record(AuditEventBuilder.forAction(AuditActions.EXPORT_SNAPSHOT_CREATE)
            .actorUser(1001L)
            .resource("export_snapshot", 500L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("insert audit log");
    }

    @Test
    void recordRequiresNew_isTransactionalBoundaryAndDelegatesToRecord() throws Exception {
        when(auditLogMapper.insert(any())).thenReturn(1);

        service.recordRequiresNew(AuditEventBuilder.forAction(AuditActions.AI_REVIEW_FAILED)
            .actorSystem()
            .resource("submission", 300L));

        verify(auditLogMapper).insert(any());
        Method record = AuditLogServiceImpl.class.getMethod("record", AuditEventBuilder.class);
        assertThat(record.getAnnotation(Transactional.class)).isNull();
        Transactional tx = AuditLogServiceImpl.class
            .getMethod("recordRequiresNew", AuditEventBuilder.class)
            .getAnnotation(Transactional.class);
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void noop_doesNotCallMapper() {
        assertThatCode(() -> AuditLogService.noop().record(AuditEventBuilder.forAction(AuditActions.TASK_DELETE)
            .actorUser(1001L)
            .resource("task", 100L))).doesNotThrowAnyException();
        assertThatCode(() -> AuditLogService.noop().recordRequiresNew(AuditEventBuilder.forAction(AuditActions.AI_REVIEW_FAILED)
            .actorSystem()
            .resource("submission", 300L))).doesNotThrowAnyException();

        verify(auditLogMapper, never()).insert(any());
    }
}
