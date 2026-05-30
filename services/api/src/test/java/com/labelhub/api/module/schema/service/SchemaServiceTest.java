package com.labelhub.api.module.schema.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldOption;
import com.labelhub.api.generated.model.SchemaFieldType;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.schema.entity.LabelSchemaEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import com.labelhub.api.module.schema.exception.SchemaAccessDeniedException;
import com.labelhub.api.module.schema.exception.SchemaNotFoundException;
import com.labelhub.api.module.schema.exception.SchemaVersionNotFoundException;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.LabelSchemaMapper;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.schema.runtime.SchemaRuntimeAdapter;
import com.labelhub.api.module.schema.service.view.SubmissionRenderSchemaView;
import com.labelhub.api.module.schema.util.SchemaValidator;
import com.labelhub.api.module.schema.util.StableIdExtractor;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskAccessDeniedException;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-05-24T12:00:00");

    private final LabelSchemaMapper labelSchemaMapper = mock(LabelSchemaMapper.class);
    private final SchemaVersionMapper schemaVersionMapper = mock(SchemaVersionMapper.class);
    private final SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Canonicalizer canonicalizer = new Canonicalizer(objectMapper);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final SchemaRuntimeAdapter schemaRuntimeAdapter = new SchemaRuntimeAdapter(objectMapper);
    private SchemaService schemaService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-24T12:00:00Z"), ZoneOffset.UTC);
        schemaService = new SchemaService(
                labelSchemaMapper,
                schemaVersionMapper,
                submissionMapper,
                taskMapper,
                new SchemaValidator(),
                new StableIdExtractor(),
                objectMapper,
                canonicalizer,
                clock,
                auditLogService
        );
    }

    @Test
    void create_succeeds_with_valid_input() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        doAnswer(invocation -> {
            LabelSchemaEntity entity = invocation.getArgument(0);
            entity.setId(55L);
            return 1;
        }).when(labelSchemaMapper).insert(any(LabelSchemaEntity.class));

        LabelSchemaEntity created = schemaService.create(10L, "Image QA", "Schema for images", 1001L);

        assertThat(created.getId()).isEqualTo(55L);
        assertThat(created.getTaskId()).isEqualTo(10L);
        assertThat(created.getOwnerId()).isEqualTo(1001L);
        assertThat(created.getCurrentVersionId()).isNull();
    }

    @Test
    void create_rejects_when_task_owner_mismatch() {
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 2002L));

        assertThatThrownBy(() -> schemaService.create(10L, "Image QA", null, 1001L))
                .isInstanceOf(TaskAccessDeniedException.class);
        verify(labelSchemaMapper, never()).insert(any(LabelSchemaEntity.class));
    }

    @Test
    void create_rejects_when_task_not_found() {
        when(taskMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> schemaService.create(10L, "Image QA", null, 1001L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void list_returns_paged_results_for_owner_only() {
        Page<LabelSchemaEntity> page = new Page<>(2, 10);
        page.setTotal(1);
        page.setRecords(List.of(schema(5L, 10L, 1001L)));
        when(labelSchemaMapper.selectPage(any(Page.class), any())).thenReturn(page);

        Page<LabelSchemaEntity> result = schemaService.list(1001L, 2, 10, null);

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getRecords()).extracting(LabelSchemaEntity::getId).containsExactly(5L);
        verify(labelSchemaMapper).selectPage(any(Page.class), any());
    }

    @Test
    void getById_returns_schema_for_owner() {
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));

        LabelSchemaEntity result = schemaService.getById(5L, 1001L);

        assertThat(result.getId()).isEqualTo(5L);
    }

    @Test
    void getById_throws_when_schema_not_found() {
        when(labelSchemaMapper.selectById(5L)).thenReturn(null);

        assertThatThrownBy(() -> schemaService.getById(5L, 1001L))
                .isInstanceOf(SchemaNotFoundException.class);
    }

    @Test
    void getById_throws_when_owner_mismatch() {
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 2002L));

        assertThatThrownBy(() -> schemaService.getById(5L, 1001L))
                .isInstanceOf(SchemaAccessDeniedException.class);
    }

    @Test
    void publishVersion_assigns_version_1_for_first_publish() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        SchemaVersionEntity version = schemaService.publishVersion(5L, simpleDocument(), 1001L);

        assertThat(version.getVersionNumber()).isEqualTo(1);
    }

    @Test
    void publishVersion_increments_version_number_sequentially() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(2);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        SchemaVersionEntity version = schemaService.publishVersion(5L, simpleDocument(), 1001L);

        assertThat(version.getVersionNumber()).isEqualTo(3);
    }

    @Test
    void publishVersion_computes_canonical_content_hash() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);
        SchemaDocument document = simpleDocument();

        SchemaVersionEntity version = schemaService.publishVersion(5L, document, 1001L);

        Map<String, Object> schemaMap = schemaRuntimeAdapter.toStorageJson(objectMapper.convertValue(document, new TypeReference<>() {}));
        assertThat(version.getContentHash()).isEqualTo(canonicalizer.sha256Hex(canonicalizer.canonicalJson(schemaMap)));
        assertThat(version.getContentHash()).hasSize(64);
    }

    @Test
    void publishVersion_storesNewSchemaAsJsonSchemaV2() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        SchemaVersionEntity version = schemaService.publishVersion(5L, simpleDocument(), 1001L);

        assertThat(version.getSchemaJson()).containsEntry("x-labelhub-schemaFormatVersion", 2);
        assertThat(version.getSchemaJson()).containsEntry("type", "object");
        assertThat(version.getSchemaJson()).containsKey("properties");
        assertThat(version.getSchemaJson()).containsKey("x-labelhub-fields");
        assertThat(version.getSchemaJson()).doesNotContainKey("fields");
    }

    @Test
    void publishVersion_extracts_all_field_stable_ids_including_nested() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        SchemaVersionEntity version = schemaService.publishVersion(5L, nestedDocument(), 1001L);

        assertThat(version.getFieldStableIds()).containsExactly("title", "profile", "age", "city");
    }

    @Test
    void publishVersion_updates_current_version_id_on_parent_schema() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        doAnswer(invocation -> {
            SchemaVersionEntity version = invocation.getArgument(0);
            version.setId(88L);
            return 1;
        }).when(schemaVersionMapper).insert(any());
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        schemaService.publishVersion(5L, simpleDocument(), 1001L);

        ArgumentCaptor<LabelSchemaEntity> parentCaptor = ArgumentCaptor.forClass(LabelSchemaEntity.class);
        verify(labelSchemaMapper).updateById(parentCaptor.capture());
        assertThat(parentCaptor.getValue().getCurrentVersionId()).isEqualTo(88L);
    }

    @Test
    void publishVersion_writesVersionCreateAndPublishAuditEvents() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        doAnswer(invocation -> {
            SchemaVersionEntity version = invocation.getArgument(0);
            version.setId(88L);
            return 1;
        }).when(schemaVersionMapper).insert(any());
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        schemaService.publishVersion(5L, simpleDocument(), 1001L);

        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService, org.mockito.Mockito.times(2)).record(captor.capture());
        assertThat(captor.getAllValues().stream().map(builder -> builder.build().action()))
            .containsExactly(AuditActions.SCHEMA_VERSION_CREATE, AuditActions.SCHEMA_PUBLISH);
        assertThat(captor.getAllValues().stream().map(builder -> builder.build().resourceType()))
            .containsExactly("schema_version", "schema");
    }

    @Test
    void publishVersion_updates_task_current_schema_version_id_when_schema_bound_to_task() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        doAnswer(invocation -> {
            SchemaVersionEntity version = invocation.getArgument(0);
            version.setId(88L);
            return 1;
        }).when(schemaVersionMapper).insert(any());
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(taskMapper.updateById(any(TaskEntity.class))).thenReturn(1);

        schemaService.publishVersion(5L, simpleDocument(), 1001L);

        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getCurrentSchemaVersionId()).isEqualTo(88L);
        assertThat(taskCaptor.getValue().getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void publishVersion_skips_task_update_when_schema_has_no_task_id() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, null, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        schemaService.publishVersion(5L, simpleDocument(), 1001L);

        verify(taskMapper, never()).selectById(any());
        verify(taskMapper, never()).updateById(any(TaskEntity.class));
    }

    @Test
    void publishVersion_rejects_invalid_schema_with_field_error() {
        SchemaDocument invalid = document(field("dup", SchemaFieldType.TEXT), field("dup", SchemaFieldType.NUMBER));

        assertThatThrownBy(() -> schemaService.publishVersion(5L, invalid, 1001L))
                .isInstanceOf(InvalidSchemaDocumentException.class)
                .isInstanceOfSatisfying(InvalidSchemaDocumentException.class, exception ->
                        assertThat(exception.getField()).isEqualTo("fields[1].stableId"));
        verify(labelSchemaMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void publishVersion_rejects_when_owner_mismatch() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 2002L));

        assertThatThrownBy(() -> schemaService.publishVersion(5L, simpleDocument(), 1001L))
                .isInstanceOf(SchemaAccessDeniedException.class);
        verify(schemaVersionMapper, never()).insert(any());
    }

    @Test
    void publishVersion_locks_parent_before_reading_max_version() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        schemaService.publishVersion(5L, simpleDocument(), 1001L);

        InOrder inOrder = inOrder(labelSchemaMapper, schemaVersionMapper);
        inOrder.verify(labelSchemaMapper).selectByIdForUpdate(5L);
        inOrder.verify(schemaVersionMapper).selectMaxVersionNumber(5L);
    }

    @Test
    void publishVersion_writes_version_before_updating_parent() {
        when(labelSchemaMapper.selectByIdForUpdate(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectMaxVersionNumber(5L)).thenReturn(null);
        when(schemaVersionMapper.insert(any())).thenReturn(1);
        when(labelSchemaMapper.updateById(any(LabelSchemaEntity.class))).thenReturn(1);

        schemaService.publishVersion(5L, simpleDocument(), 1001L);

        InOrder inOrder = inOrder(schemaVersionMapper, labelSchemaMapper);
        inOrder.verify(schemaVersionMapper).insert(any(SchemaVersionEntity.class));
        inOrder.verify(labelSchemaMapper).updateById(any(LabelSchemaEntity.class));
    }

    @Test
    void listVersions_orders_by_version_number_asc() {
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectBySchemaId(5L)).thenReturn(List.of(
                version(51L, 5L, 1, Map.of("fields", List.of("v1"))),
                version(52L, 5L, 2, Map.of("fields", List.of("v2")))
        ));

        List<SchemaVersionEntity> versions = schemaService.listVersions(5L, 1001L);

        assertThat(versions).extracting(SchemaVersionEntity::getVersionNumber).containsExactly(1, 2);
    }

    @Test
    void getVersion_returns_version_and_derives_owner_from_parent_schema() {
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectById(51L)).thenReturn(version(51L, 5L, 1, Map.of()));

        SchemaVersionEntity version = schemaService.getVersion(5L, 51L, 1001L);

        assertThat(version.getId()).isEqualTo(51L);
        assertThat(version.getOwnerId()).isEqualTo(1001L);
    }

    @Test
    void getVersion_throws_when_version_belongs_to_different_schema() {
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectById(51L)).thenReturn(version(51L, 99L, 1, Map.of()));

        assertThatThrownBy(() -> schemaService.getVersion(5L, 51L, 1001L))
                .isInstanceOf(SchemaVersionNotFoundException.class);
    }

    @Test
    void getVersion_throws_when_version_not_found() {
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));
        when(schemaVersionMapper.selectById(51L)).thenReturn(null);

        assertThatThrownBy(() -> schemaService.getVersion(5L, 51L, 1001L))
                .isInstanceOf(SchemaVersionNotFoundException.class);
    }

    @Test
    void renderForSubmission_loads_historical_schema_version() {
        SubmissionEntity submission = submission(700L, 42L, Map.of("title", "old answer"));
        SchemaVersionEntity historical = version(42L, 5L, 1, schemaJsonWithFieldCount(3));
        LabelSchemaEntity currentParent = schema(5L, 10L, 1001L);
        currentParent.setCurrentVersionId(99L);
        when(submissionMapper.selectById(700L)).thenReturn(submission);
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(schemaVersionMapper.selectById(42L)).thenReturn(historical);
        when(labelSchemaMapper.selectById(5L)).thenReturn(currentParent);

        SubmissionRenderSchemaView view = schemaService.renderForSubmission(700L, 2002L);

        assertThat(view.getSubmissionId()).isEqualTo(700L);
        assertThat(view.getSchemaVersion().getId()).isEqualTo(42L);
        assertThat(view.getSchemaVersion().getVersionNumber()).isEqualTo(1);
        assertThat(view.getSchemaVersion().getSchemaJson()).isEqualTo(schemaJsonWithFieldCount(3));
        assertThat(view.getAnswerPayload()).containsEntry("title", "old answer");
    }

    @Test
    void renderForSubmission_throws_when_submission_not_found() {
        when(submissionMapper.selectById(700L)).thenReturn(null);

        assertThatThrownBy(() -> schemaService.renderForSubmission(700L, 1001L))
                .isInstanceOf(SubmissionNotFoundException.class);
    }

    @Test
    void renderForSubmission_throws_when_bound_schema_version_missing() {
        when(submissionMapper.selectById(700L)).thenReturn(submission(700L, 42L, Map.of()));
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(schemaVersionMapper.selectById(42L)).thenReturn(null);

        assertThatThrownBy(() -> schemaService.renderForSubmission(700L, 1001L))
                .isInstanceOf(SchemaVersionNotFoundException.class);
    }

    @Test
    void renderForSubmission_throws_when_requester_is_neither_labeler_nor_owner() {
        when(submissionMapper.selectById(700L)).thenReturn(submission(700L, 42L, Map.of("title", "answer")));
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));

        assertThatThrownBy(() -> schemaService.renderForSubmission(700L, 9999L))
                .isInstanceOf(SubmissionNotFoundException.class);
        verify(schemaVersionMapper, never()).selectById(any());
        verify(labelSchemaMapper, never()).selectById(any());
    }

    @Test
    void renderForSubmission_allows_submission_labeler() {
        when(submissionMapper.selectById(700L)).thenReturn(submission(700L, 42L, Map.of("title", "answer")));
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(schemaVersionMapper.selectById(42L)).thenReturn(version(42L, 5L, 1, schemaJsonWithFieldCount(3)));
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));

        SubmissionRenderSchemaView view = schemaService.renderForSubmission(700L, 2002L);

        assertThat(view.getSchemaVersion().getOwnerId()).isEqualTo(1001L);
        assertThat(view.getAnswerPayload()).containsEntry("title", "answer");
    }

    @Test
    void renderForSubmission_allows_task_owner() {
        when(submissionMapper.selectById(700L)).thenReturn(submission(700L, 42L, Map.of("title", "answer")));
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(schemaVersionMapper.selectById(42L)).thenReturn(version(42L, 5L, 1, schemaJsonWithFieldCount(3)));
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));

        SubmissionRenderSchemaView view = schemaService.renderForSubmission(700L, 1001L);

        assertThat(view.getSchemaVersion().getOwnerId()).isEqualTo(1001L);
        assertThat(view.getAnswerPayload()).containsEntry("title", "answer");
    }

    @Test
    void renderForSubmission_allows_reviewer_to_read_any_submission() {
        when(submissionMapper.selectById(700L)).thenReturn(submission(700L, 42L, Map.of("title", "answer")));
        when(taskMapper.selectById(10L)).thenReturn(task(10L, 1001L));
        when(schemaVersionMapper.selectById(42L)).thenReturn(version(42L, 5L, 1, schemaJsonWithFieldCount(3)));
        when(labelSchemaMapper.selectById(5L)).thenReturn(schema(5L, 10L, 1001L));

        SubmissionRenderSchemaView view = schemaService.renderForSubmission(700L, 3003L, Set.of("ROLE_REVIEWER"));

        assertThat(view.getSchemaVersion().getOwnerId()).isEqualTo(1001L);
        assertThat(view.getAnswerPayload()).containsEntry("title", "answer");
    }

    private static TaskEntity task(Long id, Long ownerId) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setOwnerId(ownerId);
        return task;
    }

    private static LabelSchemaEntity schema(Long id, Long taskId, Long ownerId) {
        LabelSchemaEntity schema = new LabelSchemaEntity();
        schema.setId(id);
        schema.setTaskId(taskId);
        schema.setOwnerId(ownerId);
        schema.setName("Image QA");
        return schema;
    }

    private static SchemaVersionEntity version(Long id, Long schemaId, Integer versionNumber, Map<String, Object> schemaJson) {
        SchemaVersionEntity version = new SchemaVersionEntity();
        version.setId(id);
        version.setSchemaId(schemaId);
        version.setVersionNumber(versionNumber);
        version.setSchemaJson(schemaJson);
        version.setFieldStableIds(List.of("title"));
        version.setStatusCode("published");
        version.setPublishedAt(NOW);
        return version;
    }

    private static SubmissionEntity submission(Long id, Long schemaVersionId, Map<String, Object> answerPayload) {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(id);
        submission.setTaskId(10L);
        submission.setLabelerId(2002L);
        submission.setSchemaVersionId(schemaVersionId);
        submission.setAnswerPayload(answerPayload);
        submission.setProvenance(Map.of("source", "manual"));
        return submission;
    }

    private static SchemaDocument simpleDocument() {
        return document(field("title", SchemaFieldType.TEXT));
    }

    private static SchemaDocument nestedDocument() {
        SchemaField nested = field("profile", SchemaFieldType.NESTED_OBJECT);
        nested.setChildren(List.of(field("age", SchemaFieldType.NUMBER), field("city", SchemaFieldType.TEXT)));
        return document(field("title", SchemaFieldType.TEXT), nested);
    }

    private static SchemaDocument document(SchemaField... fields) {
        SchemaDocument document = new SchemaDocument();
        document.setFields(List.of(fields));
        return document;
    }

    private static SchemaField field(String stableId, SchemaFieldType type) {
        SchemaField field = new SchemaField();
        field.setStableId(stableId);
        field.setLabel("Label " + stableId);
        field.setType(type);
        if (type == SchemaFieldType.SINGLE_SELECT || type == SchemaFieldType.MULTI_SELECT) {
            SchemaFieldOption option = new SchemaFieldOption();
            option.setLabel("Option");
            option.setValue("option");
            field.setOptions(List.of(option));
        }
        if (type == SchemaFieldType.NESTED_OBJECT) {
            field.setChildren(List.of(field(stableId + "-child", SchemaFieldType.TEXT)));
        }
        return field;
    }

    private static Map<String, Object> schemaJsonWithFieldCount(int fieldCount) {
        return Map.of("fields", List.of("field-count-" + fieldCount));
    }
}
