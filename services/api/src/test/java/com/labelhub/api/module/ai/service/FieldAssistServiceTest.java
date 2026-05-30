package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.FieldAssistRequest;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallStatusCodes;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.AiCallUsage;
import com.labelhub.api.module.ai.provider.AiProvider;
import com.labelhub.api.module.ai.provider.ProviderInvocationResult;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.runtime.SchemaRuntimeAdapter;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.service.SessionService;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldAssistServiceTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final SchemaVersionMapper schemaVersionMapper = mock(SchemaVersionMapper.class);
    private final DatasetItemMapper datasetItemMapper = mock(DatasetItemMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AiCallMapper aiCallMapper = mock(AiCallMapper.class);
    private final AiProvider aiProvider = mock(AiProvider.class);
    private final AiCallCostCalculator costCalculator = mock(AiCallCostCalculator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FieldAssistService service = new FieldAssistService(
        sessionService,
        schemaVersionMapper,
        datasetItemMapper,
        taskMapper,
        aiCallMapper,
        aiProvider,
        costCalculator,
        new Canonicalizer(objectMapper),
        objectMapper,
        Clock.fixed(Instant.parse("2026-05-30T12:00:00Z"), ZoneOffset.UTC),
        new SchemaRuntimeAdapter(objectMapper)
    );

    @Test
    void assistPersistsFieldEvidenceWithoutSubmissionVerdict() {
        SessionEntity session = new SessionEntity();
        session.setId(55L);
        session.setTaskId(44L);
        session.setDatasetItemId(33L);
        session.setSchemaVersionId(22L);
        when(sessionService.assertLabelerOwnsSession(55L, 2002L)).thenReturn(session);
        when(schemaVersionMapper.selectById(22L)).thenReturn(schemaVersion());
        when(datasetItemMapper.selectById(33L)).thenReturn(datasetItem());
        when(taskMapper.selectById(44L)).thenReturn(task());
        when(aiProvider.providerName()).thenReturn("mock");
        when(aiProvider.modelName()).thenReturn("mock-v1");
        when(aiProvider.invokeWithUsage(any())).thenReturn(invocation());
        when(costCalculator.computeCost(any(), any())).thenReturn(new BigDecimal("0.000100"));
        when(aiCallMapper.insert(any())).thenAnswer(invocation -> {
            AiCallEntity entity = invocation.getArgument(0);
            entity.setId(77L);
            return 1;
        });

        var response = service.assist(
            new FieldAssistRequest(55L, "summary", Map.of("value", "draft")),
            2002L
        );

        assertThat(response.getAiCallId()).isEqualTo(77L);
        assertThat(response.getFieldPath()).isEqualTo("summary");
        assertThat(response.getProvenance().getAccepted()).isFalse();
        assertThat(response.getProvenance().getSource()).isEqualTo("field-assist-v1");

        ArgumentCaptor<AiCallEntity> aiCall = ArgumentCaptor.forClass(AiCallEntity.class);
        verify(aiCallMapper).insert(aiCall.capture());
        assertThat(aiCall.getValue().getSubmissionId()).isNull();
        assertThat(aiCall.getValue().getPurpose()).isEqualTo("field_assist");
        assertThat(aiCall.getValue().getFieldPath()).isEqualTo("summary");
        assertThat(aiCall.getValue().getStatus()).isEqualTo(AiCallStatusCodes.COMPLETED);
        assertThat(aiCall.getValue().getRequestPayload()).containsKeys("businessPrompt", "renderedPrompt", "input", "normalizedOutputShape");
    }

    private SchemaVersionEntity schemaVersion() {
        SchemaVersionEntity entity = new SchemaVersionEntity();
        entity.setId(22L);
        entity.setSchemaJson(Map.of("fields", List.of(Map.of(
            "stableId", "summary",
            "label", "Summary",
            "type", "llm_interaction"
        ))));
        return entity;
    }

    private DatasetItemEntity datasetItem() {
        DatasetItemEntity entity = new DatasetItemEntity();
        entity.setId(33L);
        entity.setItemPayload(Map.of("body", "source text"));
        return entity;
    }

    private TaskEntity task() {
        TaskEntity entity = new TaskEntity();
        entity.setId(44L);
        entity.setTitle("Task");
        entity.setDescription("Description");
        return entity;
    }

    private ProviderInvocationResult invocation() {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("overallSuggestion", "manual_review");
        output.put("confidence", new BigDecimal("0.8"));
        output.put("summary", "Suggested rewrite");
        output.put("dimensionScores", List.of());
        output.put("fieldFindings", List.of());
        return new ProviderInvocationResult(
            new AiCallResult(output, "manual_review", new BigDecimal("0.8"), "Suggested rewrite", List.of(), 12, 4, BigDecimal.ZERO, 25, "{}"),
            new AiCallUsage(12, 4, 16, 0)
        );
    }
}
