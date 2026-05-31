package com.labelhub.api.module.export.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEvent;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.exception.ExportFailureException;
import com.labelhub.api.module.export.mapper.ExportJobMapper;
import com.labelhub.api.module.export.mapper.ExportSnapshotMapper;
import com.labelhub.api.module.export.storage.ObjectStorageProperties;
import com.labelhub.api.module.export.storage.ObjectStorageWriter;
import com.labelhub.api.module.quality.entity.QualityLedgerEntryEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

class ExportServiceTest {

    private static final Long TASK_ID = 100L;
    private static final Long OWNER_ID = 1001L;
    private static final Long OTHER_OWNER_ID = 2002L;
    private static final Long SUBMISSION_ID = 300L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.parse("2026-05-25T10:00:00");

    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final ExportJobMapper exportJobMapper = mock(ExportJobMapper.class);
    private final ExportSnapshotMapper exportSnapshotMapper = mock(ExportSnapshotMapper.class);
    private final ExportFactCollector factCollector = mock(ExportFactCollector.class);
    private final S3Client s3Client = mock(S3Client.class);
    private final ObjectStorageWriter storageWriter = new ObjectStorageWriter(
        s3Client,
        new ObjectStorageProperties("http://localhost:9000", "us-east-1", "test", "test", "labelhub-exports", true)
    );
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Canonicalizer canonicalizer = new Canonicalizer(objectMapper);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-05-25T10:00:00Z"));

    private ExportService exportService;
    private ExportFactBundle baseBundle;

    @BeforeEach
    void setUp() {
        exportService = new ExportService(
            taskMapper,
            exportJobMapper,
            exportSnapshotMapper,
            factCollector,
            new ExportArtifactBuilder(canonicalizer),
            storageWriter,
            objectMapper,
            clock,
            auditLogService
        );
        baseBundle = bundleWithLedgerEntries(List.of(ledgerEntry(700L, "approve")));
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(OWNER_ID));
        when(factCollector.collectForTask(TASK_ID, ExportDataScope.APPROVED_ONLY)).thenReturn(baseBundle);
        when(factCollector.collectForTask(TASK_ID, ExportDataScope.FULL))
            .thenReturn(bundleWithLedgerEntries(List.of(ledgerEntry(700L, "approve")), ExportDataScope.FULL));
        stubGeneratedIds();
    }

    @Test
    void two_independent_exports_for_same_task_produce_identical_hash() {
        ExportSnapshotEntity snap1 = exportService.createSnapshot(TASK_ID, OWNER_ID);
        clock.advance(Duration.ofMillis(1));
        ExportSnapshotEntity snap2 = exportService.createSnapshot(TASK_ID, OWNER_ID);

        assertThat(snap1.getId()).isNotEqualTo(snap2.getId());
        assertThat(snap1.getExportJobId()).isNotEqualTo(snap2.getExportJobId());
        assertThat(snap1.getGeneratedAt()).isNotEqualTo(snap2.getGeneratedAt());
        assertThat(snap1.getObjectKey()).isNotEqualTo(snap2.getObjectKey());

        assertThat(snap1.getManifestHash()).isEqualTo(snap2.getManifestHash());
        assertThat(snap1.getSourceStateHash()).isEqualTo(snap2.getSourceStateHash());
        assertThat(snap1.getFileHash()).isEqualTo(snap2.getFileHash());
        assertAuditEvent(AuditActions.EXPORT_SNAPSHOT_CREATE, "user", "export_snapshot");

        verify(s3Client, times(26)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void manifest_hash_excludes_runtime_metadata() {
        ExportSnapshotEntity snap1 = exportService.createSnapshot(TASK_ID, OWNER_ID);
        clock.advance(Duration.ofSeconds(2));
        ExportSnapshotEntity snap2 = exportService.createSnapshot(TASK_ID, OWNER_ID);

        assertThat(snap1.getExportJobId()).isNotEqualTo(snap2.getExportJobId());
        assertThat(snap1.getGeneratedAt()).isNotEqualTo(snap2.getGeneratedAt());
        assertThat(snap1.getObjectKey()).isNotEqualTo(snap2.getObjectKey());
        assertThat(snap1.getManifestHash()).isEqualTo(snap2.getManifestHash());
    }

    @Test
    void export_hash_changes_when_new_ledger_entry_appended() {
        ExportSnapshotEntity snap1 = exportService.createSnapshot(TASK_ID, OWNER_ID);

        when(factCollector.collectForTask(TASK_ID, ExportDataScope.APPROVED_ONLY))
            .thenReturn(bundleWithLedgerEntries(List.of(
                ledgerEntry(700L, "approve"),
                ledgerEntry(701L, "reject")
            )));
        clock.advance(Duration.ofMillis(1));

        ExportSnapshotEntity snap2 = exportService.createSnapshot(TASK_ID, OWNER_ID);

        assertThat(snap2.getManifestHash()).isNotEqualTo(snap1.getManifestHash());
        assertThat(snap2.getSourceStateHash()).isNotEqualTo(snap1.getSourceStateHash());
        assertThat(snap2.getFileHash()).isNotEqualTo(snap1.getFileHash());
    }

    @Test
    void export_includes_derived_verdict_snapshot() throws Exception {
        exportService.createSnapshot(TASK_ID, OWNER_ID);

        String verdicts = writtenUtf8("verdicts.jsonl");

        assertThat(verdicts).contains("\"submissionId\":300");
        assertThat(verdicts).contains("\"status\":\"approved\"");
        assertThat(verdicts).contains("\"derivedFromEntryId\":700");
        assertThat(verdicts).doesNotContain("derivedAt");
    }

    @Test
    void createSnapshot_defaults_to_approved_only_export_scope() throws Exception {
        ExportSnapshotEntity snapshot = exportService.createSnapshot(TASK_ID, OWNER_ID);

        verify(factCollector).collectForTask(TASK_ID, ExportDataScope.APPROVED_ONLY);
        assertThat(snapshot.getDataScope())
            .containsEntry("type", "approved_only")
            .containsEntry("mode", "approved_only")
            .containsEntry("verdict", "approved");
        Map<String, Object> manifest = json(writtenUtf8("manifest.json"));
        Map<String, Object> content = castMap(manifest.get("content"));
        assertThat(castMap(content.get("dataScope")))
            .containsEntry("type", "approved_only")
            .containsEntry("mode", "approved_only");
    }

    @Test
    void createSnapshot_accepts_full_compatibility_scope() throws Exception {
        ExportSnapshotEntity snapshot = exportService.createSnapshot(TASK_ID, OWNER_ID, ExportDataScope.FULL);

        verify(factCollector).collectForTask(TASK_ID, ExportDataScope.FULL);
        assertThat(snapshot.getDataScope())
            .containsEntry("type", "task_complete")
            .containsEntry("mode", "full");
        Map<String, Object> manifest = json(writtenUtf8("manifest.json"));
        Map<String, Object> content = castMap(manifest.get("content"));
        assertThat(castMap(content.get("dataScope"))).containsEntry("mode", "full");
    }

    @Test
    void createSnapshot_snapshots_field_mapping_into_manifest_and_entity() throws Exception {
        ExportSnapshotEntity snapshot = exportService.createSnapshot(
            TASK_ID,
            OWNER_ID,
            ExportDataScope.APPROVED_ONLY,
            new ExportFieldMapping(List.of(
                new ExportFieldMappingColumn("item.text", "prompt_text", true),
                new ExportFieldMappingColumn("answer.title", "label", true)
            ))
        );

        Map<String, Object> manifest = json(writtenUtf8("manifest.json"));
        Map<String, Object> content = castMap(manifest.get("content"));
        Map<String, Object> mapping = castMap(content.get("fieldMappingSnapshot"));

        assertThat(snapshot.getFieldMappingSnapshot()).isEqualTo(mapping);
        assertThat(castList(mapping.get("columns"))).hasSize(2);
        assertThat(writtenUtf8("training-results.csv")).startsWith("prompt_text,label\n");
    }

    @Test
    void export_omits_null_prompt_and_rule_evidence_fields_from_ai_calls() throws Exception {
        when(factCollector.collectForTask(TASK_ID, ExportDataScope.APPROVED_ONLY))
            .thenReturn(bundleWithAiCall(legacyAiCallWithoutEvidenceBindings()));

        exportService.createSnapshot(TASK_ID, OWNER_ID);

        String aiCalls = writtenUtf8("ai-calls.jsonl");

        assertThat(aiCalls).contains("\"promptVersion\":\"m3-owner-review-v1\"");
        assertThat(aiCalls).contains("\"providerAdapterVersion\":\"agent-default-v1\"");
        assertThat(aiCalls).contains("\"fieldPath\":null");
        assertThat(aiCalls).doesNotContain("\"promptVersionId\"");
        assertThat(aiCalls).doesNotContain("\"aiReviewRuleId\"");
    }

    @Test
    void export_includes_non_null_prompt_and_rule_evidence_fields_in_ai_calls() throws Exception {
        AiCallEntity ruleBound = aiCall();
        ruleBound.setAiReviewRuleId(19L);
        when(factCollector.collectForTask(TASK_ID, ExportDataScope.APPROVED_ONLY)).thenReturn(bundleWithAiCall(ruleBound));

        exportService.createSnapshot(TASK_ID, OWNER_ID);

        String aiCalls = writtenUtf8("ai-calls.jsonl");

        assertThat(aiCalls).contains("\"promptVersionId\":1");
        assertThat(aiCalls).contains("\"aiReviewRuleId\":19");
        assertThat(aiCalls).contains("\"providerAdapterVersion\":\"agent-default-v1\"");
    }

    @Test
    void export_manifest_includes_sha256_for_each_file() throws Exception {
        exportService.createSnapshot(TASK_ID, OWNER_ID);

        Map<String, Object> manifest = json(writtenUtf8("manifest.json"));
        Map<String, Object> content = castMap(manifest.get("content"));
        List<Map<String, Object>> files = castList(content.get("files"));

        assertThat(files).hasSize(12);
        assertThat(files)
            .allSatisfy(file -> {
                assertThat(file.get("name")).isNotNull();
                assertThat((String) file.get("sha256")).matches("^[a-f0-9]{64}$");
                assertThat(file.get("lines")).isNotNull();
                assertThat(file.get("sizeBytes")).isNotNull();
            });
    }

    @Test
    void export_rejects_when_task_not_owned_by_requester() {
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(OTHER_OWNER_ID));

        assertThatThrownBy(() -> exportService.createSnapshot(TASK_ID, OWNER_ID))
            .isInstanceOf(TaskNotFoundException.class);

        verify(exportJobMapper, never()).insert(any());
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void export_with_no_submissions_creates_empty_jsonl_files() throws Exception {
        when(factCollector.collectForTask(TASK_ID, ExportDataScope.APPROVED_ONLY)).thenReturn(emptyBundle());

        exportService.createSnapshot(TASK_ID, OWNER_ID);

        assertThat(writtenUtf8("submissions.jsonl")).isEmpty();
        assertThat(writtenUtf8("answers.jsonl")).isEmpty();
        assertThat(writtenUtf8("verdicts.jsonl")).isEmpty();
    }

    @Test
    void export_source_state_keeps_dataset_item_payload_hash_and_status_from_collected_facts() throws Exception {
        exportService.createSnapshot(TASK_ID, OWNER_ID);

        String datasetItems = writtenUtf8("dataset-items.jsonl");

        assertThat(datasetItems).contains("\"itemPayload\":{\"text\":\"source\"}");
        assertThat(datasetItems).contains("\"itemHash\":\"item-hash\"");
        assertThat(datasetItems).contains("\"status\":\"completed\"");
    }

    @Test
    void partial_export_failure_deletes_written_object_keys() {
        failPutObjectOnAttempt(3);

        assertThatThrownBy(() -> exportService.createSnapshot(TASK_ID, OWNER_ID))
            .isInstanceOf(ExportFailureException.class);

        assertThat(deletedKeys()).containsExactly(
            "exports/tasks/100/jobs/10/task.json",
            "exports/tasks/100/jobs/10/source-state.json"
        );
    }

    @Test
    void cleanup_does_not_delete_unrelated_objects() {
        failPutObjectOnAttempt(3);

        assertThatThrownBy(() -> exportService.createSnapshot(TASK_ID, OWNER_ID))
            .isInstanceOf(ExportFailureException.class);

        assertThat(deletedKeys())
            .allMatch(key -> key.startsWith("exports/tasks/100/jobs/10/"))
            .noneMatch(key -> key.startsWith("exports/tasks/100/jobs/11/"));
    }

    @Test
    void cleanup_failure_preserves_original_export_failure() {
        failPutObjectOnAttempt(2);
        doThrow(new RuntimeException("cleanup failed"))
            .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> exportService.createSnapshot(TASK_ID, OWNER_ID))
            .isInstanceOf(ExportFailureException.class)
            .hasRootCauseMessage("put failed at attempt 2");
    }

    @Test
    void successful_export_does_not_trigger_cleanup() {
        exportService.createSnapshot(TASK_ID, OWNER_ID);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void no_failed_export_job_persistence() {
        failPutObjectOnAttempt(2);

        assertThatThrownBy(() -> exportService.createSnapshot(TASK_ID, OWNER_ID))
            .isInstanceOf(ExportFailureException.class);

        ArgumentCaptor<ExportJobEntity> jobCaptor = ArgumentCaptor.forClass(ExportJobEntity.class);
        verify(exportJobMapper).insert(jobCaptor.capture());
        assertThat(jobCaptor.getAllValues())
            .extracting(ExportJobEntity::getStatus)
            .doesNotContain("failed");
    }

    @Test
    void listSnapshotsForOwner_enforces_task_ownership_and_returns_page() {
        ExportSnapshotEntity snapshot = snapshot(100L, TASK_ID, "aaa");
        when(exportSnapshotMapper.selectByTaskId(TASK_ID, 0L, 20L)).thenReturn(List.of(snapshot));
        when(exportSnapshotMapper.selectCountByTaskId(TASK_ID)).thenReturn(1L);

        var page = exportService.listSnapshotsForOwner(TASK_ID, OWNER_ID, 1, 20);

        assertThat(page.items()).containsExactly(snapshot);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void getSnapshotForOwner_hides_cross_owner_snapshot() {
        ExportSnapshotEntity snapshot = snapshot(100L, TASK_ID, "aaa");
        when(exportSnapshotMapper.selectById(100L)).thenReturn(snapshot);
        when(taskMapper.selectById(TASK_ID)).thenReturn(task(OTHER_OWNER_ID));

        assertThatThrownBy(() -> exportService.getSnapshotForOwner(100L, OWNER_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Export snapshot not found");
    }

    @Test
    void diffSnapshotsForOwner_compares_three_hash_layers_and_file_hashes() {
        ExportSnapshotEntity base = snapshot(100L, TASK_ID, "aaa");
        ExportSnapshotEntity compare = snapshot(101L, TASK_ID, "bbb");
        compare.setFileHash(base.getFileHash());
        compare.setManifestHash("different-manifest");
        compare.setSourceStateHash("different-source");
        when(exportSnapshotMapper.selectById(100L)).thenReturn(base);
        when(exportSnapshotMapper.selectById(101L)).thenReturn(compare);

        ExportSnapshotDiffView diff = exportService.diffSnapshotsForOwner(100L, 101L, OWNER_ID);

        assertThat(diff.equal()).isFalse();
        assertThat(diff.fileHashMatch()).isTrue();
        assertThat(diff.manifestHashMatch()).isFalse();
        assertThat(diff.sourceStateHashMatch()).isFalse();
        assertThat(diff.fileLevelMatches()).hasSize(2);
        assertThat(diff.fileLevelMatches())
            .anySatisfy(match -> {
                assertThat(match.fileName()).isEqualTo("answers.jsonl");
                assertThat(match.match()).isFalse();
            });
    }

    private void stubGeneratedIds() {
        AtomicLong jobIds = new AtomicLong(10L);
        AtomicLong snapshotIds = new AtomicLong(100L);
        when(exportJobMapper.insert(any())).thenAnswer(invocation -> {
            ExportJobEntity job = invocation.getArgument(0);
            job.setId(jobIds.getAndIncrement());
            return 1;
        });
        when(exportSnapshotMapper.insert(any())).thenAnswer(invocation -> {
            ExportSnapshotEntity snapshot = invocation.getArgument(0);
            snapshot.setId(snapshotIds.getAndIncrement());
            return 1;
        });
    }

    private void failPutObjectOnAttempt(int failingAttempt) {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == failingAttempt) {
                throw new RuntimeException("put failed at attempt " + failingAttempt);
            }
            return null;
        }).when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private List<String> deletedKeys() {
        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(requestCaptor.capture());
        return requestCaptor.getAllValues().stream().map(DeleteObjectRequest::key).toList();
    }

    private String writtenUtf8(String suffix) {
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, times(13)).putObject(requestCaptor.capture(), bodyCaptor.capture());
        List<PutObjectRequest> requests = requestCaptor.getAllValues();
        List<RequestBody> bodies = bodyCaptor.getAllValues();
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).key().endsWith(suffix)) {
                return new String(readAllBytes(bodies.get(i)));
            }
        }
        throw new AssertionError("No object written with suffix " + suffix);
    }

    private static byte[] readAllBytes(RequestBody body) {
        try {
            return body.contentStreamProvider().newStream().readAllBytes();
        } catch (Exception e) {
            throw new AssertionError("Could not read captured request body", e);
        }
    }

    private Map<String, Object> json(String value) throws Exception {
        return objectMapper.readValue(value, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static ExportFactBundle emptyBundle() {
        return new ExportFactBundle(
            task(OWNER_ID),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            ExportDataScope.APPROVED_ONLY
        );
    }

    private static ExportFactBundle bundleWithAiCall(AiCallEntity aiCall) {
        LinkedHashMap<Long, DerivedVerdictSnapshot> verdicts = new LinkedHashMap<>();
        QualityLedgerEntryEntity latest = ledgerEntry(700L, "approve");
        verdicts.put(SUBMISSION_ID, DerivedVerdictSnapshot.derive(SUBMISSION_ID, latest));
        return new ExportFactBundle(
            task(OWNER_ID),
            List.of(schemaVersion()),
            List.of(datasetItem()),
            List.of(submission()),
            List.of(aiCall),
            List.of(aiCallInField()),
            List.of(latest),
            verdicts,
            ExportDataScope.APPROVED_ONLY
        );
    }

    private static ExportFactBundle bundleWithLedgerEntries(List<QualityLedgerEntryEntity> ledgerEntries) {
        return bundleWithLedgerEntries(ledgerEntries, ExportDataScope.APPROVED_ONLY);
    }

    private static ExportFactBundle bundleWithLedgerEntries(
        List<QualityLedgerEntryEntity> ledgerEntries,
        ExportDataScope dataScope
    ) {
        LinkedHashMap<Long, DerivedVerdictSnapshot> verdicts = new LinkedHashMap<>();
        QualityLedgerEntryEntity latest = ledgerEntries.get(ledgerEntries.size() - 1);
        verdicts.put(SUBMISSION_ID, DerivedVerdictSnapshot.derive(SUBMISSION_ID, latest));
        return new ExportFactBundle(
            task(OWNER_ID),
            List.of(schemaVersion()),
            List.of(datasetItem()),
            List.of(submission()),
            List.of(aiCall()),
            List.of(aiCallInField()),
            ledgerEntries,
            verdicts,
            dataScope
        );
    }

    private static TaskEntity task(Long ownerId) {
        TaskEntity task = new TaskEntity();
        task.setId(TASK_ID);
        task.setOwnerId(ownerId);
        task.setTitle("Export task");
        task.setDescription("Trusted export demo");
        task.setStatusCode("published");
        task.setCurrentDatasetId(40L);
        task.setCurrentSchemaVersionId(50L);
        task.setCreatedAt(CREATED_AT.minusDays(1));
        task.setUpdatedAt(CREATED_AT);
        return task;
    }

    private static SchemaVersionEntity schemaVersion() {
        SchemaVersionEntity version = new SchemaVersionEntity();
        version.setId(50L);
        version.setSchemaId(5L);
        version.setVersionNumber(1);
        version.setSchemaJson(Map.of("fields", List.of(Map.of("stableId", "title", "type", "text"))));
        version.setFieldStableIds(List.of("title"));
        version.setContentHash("schema-hash");
        version.setStatusCode("published");
        version.setPublishedAt(CREATED_AT.minusHours(1));
        version.setCreatedAt(CREATED_AT.minusHours(2));
        return version;
    }

    private static DatasetItemEntity datasetItem() {
        DatasetItemEntity item = new DatasetItemEntity();
        item.setId(80L);
        item.setDatasetId(40L);
        item.setTaskId(TASK_ID);
        item.setOrdinal(1);
        item.setItemPayload(Map.of("text", "source"));
        item.setItemHash("item-hash");
        item.setStatus("completed");
        item.setCreatedAt(CREATED_AT.minusHours(3));
        return item;
    }

    private static SubmissionEntity submission() {
        SubmissionEntity submission = new SubmissionEntity();
        submission.setId(SUBMISSION_ID);
        submission.setSessionId(200L);
        submission.setTaskId(TASK_ID);
        submission.setDatasetItemId(80L);
        submission.setLabelerId(1200L);
        submission.setSchemaVersionId(50L);
        submission.setAnswerPayload(Map.of("title", "answer"));
        submission.setProvenance(Map.of("client", "web"));
        submission.setContentHash("answer-hash");
        submission.setStatusCode("submitted");
        submission.setCreatedAt(CREATED_AT);
        return submission;
    }

    private static AiCallEntity aiCall() {
        AiCallEntity call = new AiCallEntity();
        call.setId(600L);
        call.setSubmissionId(SUBMISSION_ID);
        call.setPurpose("owner_review");
        call.setPromptVersion("promptVersion#1");
        call.setPromptVersionId(1L);
        call.setProviderAdapterVersion("agent-default-v1");
        call.setModelProvider("mock");
        call.setModelName("mock-reviewer");
        call.setInputHash("input-hash");
        call.setResponsePayload(Map.of("overallSuggestion", "looks_good"));
        call.setScores(Map.of("confidence", 0.9));
        call.setVerdict("looks_good");
        call.setStatus("completed");
        call.setCreatedAt(CREATED_AT.plusMinutes(1));
        call.setCompletedAt(CREATED_AT.plusMinutes(1).plusSeconds(1));
        return call;
    }

    private static AiCallEntity legacyAiCallWithoutEvidenceBindings() {
        AiCallEntity call = aiCall();
        call.setPromptVersion("m3-owner-review-v1");
        call.setPromptVersionId(null);
        call.setAiReviewRuleId(null);
        return call;
    }

    private static AiCallInFieldEntity aiCallInField() {
        AiCallInFieldEntity field = new AiCallInFieldEntity();
        field.setId(610L);
        field.setSubmissionId(SUBMISSION_ID);
        field.setFieldPath("title");
        field.setAiCallId(600L);
        field.setAccepted(true);
        field.setUserModifiedAfter(false);
        field.setOrdinal(1);
        field.setCreatedAt(CREATED_AT.plusMinutes(1));
        return field;
    }

    private static QualityLedgerEntryEntity ledgerEntry(Long id, String verdict) {
        QualityLedgerEntryEntity entry = new QualityLedgerEntryEntity();
        entry.setId(id);
        entry.setSubmissionId(SUBMISSION_ID);
        entry.setTaskId(TASK_ID);
        entry.setEvidenceType("reviewer_overall_verdict");
        entry.setActorType("reviewer");
        entry.setActorId(3003L);
        entry.setPayload(Map.of("verdict", verdict, "reviewLevel", "senior_reviewer"));
        entry.setCreatedAt(CREATED_AT.plusMinutes(id - 700L));
        return entry;
    }

    private static ExportSnapshotEntity snapshot(Long id, Long taskId, String answerSha) {
        ExportSnapshotEntity snapshot = new ExportSnapshotEntity();
        snapshot.setId(id);
        snapshot.setExportJobId(id + 1000L);
        snapshot.setTaskId(taskId);
        snapshot.setFileHash(hash("file"));
        snapshot.setManifestHash(hash("manifest"));
        snapshot.setSourceStateHash(hash("source"));
        snapshot.setObjectKey("exports/tasks/" + taskId + "/jobs/" + (id + 1000L) + "/");
        snapshot.setFileManifest(Map.of("files", List.of(
            Map.of("name", "answers.jsonl", "sha256", answerSha, "lines", 1, "sizeBytes", 10),
            Map.of("name", "task.json", "sha256", "task-sha", "lines", 1, "sizeBytes", 20)
        )));
        snapshot.setRecordCounts(Map.of("submissions", 1));
        snapshot.setCanonicalizationVersion("labelhub-canonical-v1");
        snapshot.setGeneratedAt(CREATED_AT);
        return snapshot;
    }

    private static String hash(String value) {
        return "0".repeat(64 - value.length()) + value;
    }

    private void assertAuditEvent(String action, String actorType, String resourceType) {
        ArgumentCaptor<AuditEventBuilder> captor = ArgumentCaptor.forClass(AuditEventBuilder.class);
        verify(auditLogService, times(2)).record(captor.capture());
        AuditEvent event = captor.getAllValues().get(0).build();
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.actorType()).isEqualTo(actorType);
        assertThat(event.resourceType()).isEqualTo(resourceType);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
