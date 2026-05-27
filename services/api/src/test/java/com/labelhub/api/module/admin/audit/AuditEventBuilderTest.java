package com.labelhub.api.module.admin.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventBuilderTest {

    @Test
    void build_setsActorsAndPreservesPayloadOrder() {
        AuditEvent event = AuditEventBuilder.forAction(AuditActions.SUBMISSION_CREATE)
            .actorUser(1002L)
            .resource("submission", 300L)
            .payload("sessionId", 900L)
            .payload("taskId", 10L)
            .build();

        assertThat(event.actorType()).isEqualTo("user");
        assertThat(event.actorId()).isEqualTo(1002L);
        assertThat(event.action()).isEqualTo(AuditActions.SUBMISSION_CREATE);
        assertThat(event.resourceType()).isEqualTo("submission");
        assertThat(event.payload()).containsExactly(
            Map.entry("sessionId", 900L),
            Map.entry("taskId", 10L)
        );
        assertThatThrownBy(() -> event.payload().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void build_supportsSystemAndAiActors() {
        assertThat(AuditEventBuilder.forAction(AuditActions.AI_REVIEW_FAILED)
            .actorSystem()
            .resource("submission", 300L)
            .build()
            .actorType()).isEqualTo("system");
        assertThat(AuditEventBuilder.forAction(AuditActions.AI_REVIEW_FIELD_ASSIST)
            .actorAi()
            .resource("submission", 300L)
            .build()
            .actorType()).isEqualTo("ai");
    }

    @Test
    void build_requiresActorActionAndResourceType() {
        assertThatThrownBy(() -> AuditEventBuilder.forAction(AuditActions.TASK_DELETE)
            .resource("task", 100L)
            .build()).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("actorType");
        assertThatThrownBy(() -> AuditEventBuilder.forAction(null)
            .actorUser(1001L)
            .resource("task", 100L)
            .build()).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("action");
        assertThatThrownBy(() -> AuditEventBuilder.forAction(AuditActions.TASK_DELETE)
            .actorUser(1001L)
            .build()).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("resourceType");
    }

    @Test
    void payloadMap_replacesExistingPayload() {
        Map<String, Object> replacement = new LinkedHashMap<>();
        replacement.put("after", "replace");

        AuditEvent event = AuditEventBuilder.forAction(AuditActions.TASK_TRANSITION)
            .actorUser(1001L)
            .resource("task", 100L)
            .payload("before", "clear")
            .payload(replacement)
            .build();

        assertThat(event.payload()).containsExactly(Map.entry("after", "replace"));
    }
}
