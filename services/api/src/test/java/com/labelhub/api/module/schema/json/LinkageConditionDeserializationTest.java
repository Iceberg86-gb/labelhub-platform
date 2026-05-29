package com.labelhub.api.module.schema.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.config.JacksonConfig;
import com.labelhub.api.generated.model.LinkageAtomicCondition;
import com.labelhub.api.generated.model.LinkageConditionGroup;
import com.labelhub.api.generated.model.LinkageConditionOp;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.dataset.mapper.DatasetItemMapper;
import com.labelhub.api.module.outbox.service.OutboxEventService;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.session.mapper.DraftMapper;
import com.labelhub.api.module.session.mapper.SessionMapper;
import com.labelhub.api.module.session.service.SessionService;
import com.labelhub.api.module.submission.validation.AnswerPayloadValidator;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LinkageConditionDeserializationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .withUserConfiguration(JacksonConfig.class)
        .withBean(TaskMapper.class, () -> mock(TaskMapper.class))
        .withBean(DatasetItemMapper.class, () -> mock(DatasetItemMapper.class))
        .withBean(SessionMapper.class, () -> mock(SessionMapper.class))
        .withBean(SchemaVersionMapper.class, () -> mock(SchemaVersionMapper.class))
        .withBean(DraftMapper.class, () -> mock(DraftMapper.class))
        .withBean(SubmissionMapper.class, () -> mock(SubmissionMapper.class))
        .withBean(OutboxEventService.class, () -> mock(OutboxEventService.class))
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(AuditLogService.class, AuditLogService::noop)
        .withBean(AnswerPayloadValidator.class, AnswerPayloadValidator::new)
        .withBean(Canonicalizer.class, () -> new Canonicalizer(new ObjectMapper()))
        .withBean(SessionService.class);

    @Test
    void spring_mapper_round_trips_atomic_group_and_nested_linkage_conditions() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            ObjectMapper mapper = context.getBean(ObjectMapper.class);

            SchemaDocument roundTripped = roundTrip(mapper, linkedSchemaDocument());

            SchemaField target = roundTripped.getFields().get(2);
            assertThat(target.getVisibleWhen()).isInstanceOf(LinkageAtomicCondition.class);
            LinkageAtomicCondition visibleWhen = (LinkageAtomicCondition) target.getVisibleWhen();
            assertThat(visibleWhen.getField()).isEqualTo("driver");
            assertThat(visibleWhen.getOp()).isEqualTo(LinkageConditionOp.EQ);
            assertThat(visibleWhen.getValue()).isEqualTo("show");

            assertThat(target.getRequiredWhen()).isInstanceOf(LinkageConditionGroup.class);
            LinkageConditionGroup requiredWhen = (LinkageConditionGroup) target.getRequiredWhen();
            assertThat(requiredWhen.getAnyOf()).hasSize(2);
            assertThat(requiredWhen.getAnyOf().get(0).getField()).isEqualTo("driver");
            assertThat(requiredWhen.getAnyOf().get(1).getValue()).isEqualTo(List.of("manual", "custom"));

            SchemaField nestedChild = roundTripped.getFields().get(3).getChildren().get(0);
            assertThat(nestedChild.getVisibleWhen()).isInstanceOf(LinkageAtomicCondition.class);
            LinkageAtomicCondition nestedVisibleWhen = (LinkageAtomicCondition) nestedChild.getVisibleWhen();
            assertThat(nestedVisibleWhen.getField()).isEqualTo("driver");
            assertThat(nestedVisibleWhen.getValue()).isEqualTo("nested");
        });
    }

    @Test
    void sessionService_mainConstructor_receives_configured_spring_mapper() {
        contextRunner.run(context -> {
            SessionService sessionService = context.getBean(SessionService.class);
            Object mapperField = ReflectionTestUtils.getField(sessionService, "objectMapper");

            assertThat(mapperField).isSameAs(context.getBean(ObjectMapper.class));
            assertThat(roundTrip((ObjectMapper) mapperField, linkedSchemaDocument()).getFields().get(2).getVisibleWhen())
                .isInstanceOf(LinkageAtomicCondition.class);
        });
    }

    @Test
    void malformed_condition_shape_fails_with_controlled_mapping_error() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            Map<String, Object> malformed = Map.of(
                "fields", List.of(Map.of(
                    "stableId", "target",
                    "label", "Target",
                    "type", "text",
                    "visibleWhen", Map.of("value", "orphan")
                ))
            );

            assertThatThrownBy(() -> mapper.convertValue(malformed, SchemaDocument.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LinkageCondition must contain field/op or allOf/anyOf");
        });
    }

    private SchemaDocument roundTrip(ObjectMapper mapper, SchemaDocument document) {
        Map<String, Object> stored = mapper.convertValue(document, new TypeReference<>() {});
        return mapper.convertValue(stored, SchemaDocument.class);
    }

    private SchemaDocument linkedSchemaDocument() {
        SchemaField driver = new SchemaField()
            .stableId("driver")
            .label("Driver")
            .type(SchemaFieldType.TEXT);
        SchemaField category = new SchemaField()
            .stableId("category")
            .label("Category")
            .type(SchemaFieldType.SINGLE_SELECT)
            .options(List.of(
                option("manual", "Manual"),
                option("custom", "Custom")
            ));
        SchemaField target = new SchemaField()
            .stableId("target")
            .label("Target")
            .type(SchemaFieldType.TEXT)
            .visibleWhen(atomic("driver", LinkageConditionOp.EQ, "show"))
            .requiredWhen(groupAnyOf(
                atomic("driver", LinkageConditionOp.EQ, "required"),
                atomic("category", LinkageConditionOp.IN, List.of("manual", "custom"))
            ));
        SchemaField nestedChild = new SchemaField()
            .stableId("nested_child")
            .label("Nested Child")
            .type(SchemaFieldType.TEXT)
            .visibleWhen(atomic("driver", LinkageConditionOp.EQ, "nested"));
        SchemaField nested = new SchemaField()
            .stableId("nested")
            .label("Nested")
            .type(SchemaFieldType.NESTED_OBJECT)
            .children(List.of(nestedChild));
        return new SchemaDocument().fields(List.of(driver, category, target, nested));
    }

    private LinkageAtomicCondition atomic(String field, LinkageConditionOp op, Object value) {
        return new LinkageAtomicCondition()
            .field(field)
            .op(op)
            .value(value);
    }

    private LinkageConditionGroup groupAnyOf(LinkageAtomicCondition... conditions) {
        return new LinkageConditionGroup()
            .anyOf(List.of(conditions));
    }

    private com.labelhub.api.generated.model.SchemaFieldOption option(String value, String label) {
        return new com.labelhub.api.generated.model.SchemaFieldOption()
            .value(value)
            .label(label);
    }
}
