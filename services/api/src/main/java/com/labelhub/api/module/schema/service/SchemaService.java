package com.labelhub.api.module.schema.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.module.schema.entity.LabelSchemaEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.exception.SchemaAccessDeniedException;
import com.labelhub.api.module.schema.exception.SchemaNotFoundException;
import com.labelhub.api.module.schema.exception.SchemaVersionNotFoundException;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.schema.mapper.LabelSchemaMapper;
import com.labelhub.api.module.schema.mapper.SchemaVersionMapper;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.schema.service.view.SubmissionRenderSchemaView;
import com.labelhub.api.module.schema.util.SchemaValidator;
import com.labelhub.api.module.schema.util.StableIdExtractor;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskAccessDeniedException;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaService {

    private final LabelSchemaMapper labelSchemaMapper;
    private final SchemaVersionMapper schemaVersionMapper;
    private final SubmissionMapper submissionMapper;
    private final TaskMapper taskMapper;
    private final SchemaValidator schemaValidator;
    private final StableIdExtractor stableIdExtractor;
    private final ObjectMapper objectMapper;
    private final Canonicalizer canonicalizer;
    private final Clock clock;

    public SchemaService(
        LabelSchemaMapper labelSchemaMapper,
        SchemaVersionMapper schemaVersionMapper,
        SubmissionMapper submissionMapper,
        TaskMapper taskMapper,
        SchemaValidator schemaValidator,
        StableIdExtractor stableIdExtractor,
        ObjectMapper objectMapper,
        Canonicalizer canonicalizer,
        Clock clock
    ) {
        this.labelSchemaMapper = labelSchemaMapper;
        this.schemaVersionMapper = schemaVersionMapper;
        this.submissionMapper = submissionMapper;
        this.taskMapper = taskMapper;
        this.schemaValidator = schemaValidator;
        this.stableIdExtractor = stableIdExtractor;
        this.objectMapper = objectMapper;
        this.canonicalizer = canonicalizer;
        this.clock = clock;
    }

    public LabelSchemaEntity create(Long taskId, String name, String description, Long ownerId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new TaskNotFoundException(taskId);
        }
        if (!Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskAccessDeniedException(taskId, ownerId);
        }

        LabelSchemaEntity schema = new LabelSchemaEntity();
        schema.setTaskId(taskId);
        schema.setName(name);
        schema.setDescription(description);
        schema.setOwnerId(ownerId);
        schema.setCurrentVersionId(null);
        requireOneRow(labelSchemaMapper.insert(schema), "insert label schema");
        return schema;
    }

    public Page<LabelSchemaEntity> list(Long ownerId, long page, long size, String queryText) {
        LambdaQueryWrapper<LabelSchemaEntity> query = new LambdaQueryWrapper<LabelSchemaEntity>()
            .eq(LabelSchemaEntity::getOwnerId, ownerId);
        if (queryText != null && !queryText.isBlank()) {
            String trimmed = queryText.trim();
            query.and(wrapper -> wrapper
                .like(LabelSchemaEntity::getName, trimmed)
                .or()
                .like(LabelSchemaEntity::getDescription, trimmed));
        }
        query.orderByDesc(LabelSchemaEntity::getCreatedAt);
        return labelSchemaMapper.selectPage(Page.of(page, size), query);
    }

    public LabelSchemaEntity getById(Long schemaId, Long ownerId) {
        return assertSchemaOwnership(schemaId, ownerId);
    }

    @Transactional
    public SchemaVersionEntity publishVersion(Long schemaId, SchemaDocument schemaDocument, Long ownerId) {
        schemaValidator.validate(schemaDocument);

        LabelSchemaEntity parent = labelSchemaMapper.selectByIdForUpdate(schemaId);
        if (parent == null) {
            throw new SchemaNotFoundException(schemaId);
        }
        if (!Objects.equals(parent.getOwnerId(), ownerId)) {
            throw new SchemaAccessDeniedException(schemaId, ownerId);
        }

        Integer currentMaxVersion = schemaVersionMapper.selectMaxVersionNumber(schemaId);
        int nextVersionNumber = currentMaxVersion == null ? 1 : currentMaxVersion + 1;
        Map<String, Object> schemaJson = objectMapper.convertValue(schemaDocument, new TypeReference<>() {});
        String canonicalJson = canonicalizer.canonicalJson(schemaJson);

        SchemaVersionEntity version = new SchemaVersionEntity();
        version.setSchemaId(schemaId);
        version.setVersionNumber(nextVersionNumber);
        version.setSchemaJson(schemaJson);
        version.setFieldStableIds(stableIdExtractor.extract(schemaDocument));
        version.setContentHash(canonicalizer.sha256Hex(canonicalJson));
        version.setStatusCode("published");
        version.setPublishedAt(LocalDateTime.now(clock));
        version.setOwnerId(parent.getOwnerId());

        requireOneRow(schemaVersionMapper.insert(version), "insert schema version");
        parent.setCurrentVersionId(version.getId());
        requireOneRow(labelSchemaMapper.updateById(parent), "update label schema current version");
        if (parent.getTaskId() != null) {
            TaskEntity task = taskMapper.selectById(parent.getTaskId());
            if (task != null) {
                task.setCurrentSchemaVersionId(version.getId());
                task.setUpdatedAt(LocalDateTime.now(clock));
                requireOneRow(taskMapper.updateById(task), "update task current schema version");
            }
        }
        return version;
    }

    public List<SchemaVersionEntity> listVersions(Long schemaId, Long ownerId) {
        LabelSchemaEntity parent = assertSchemaOwnership(schemaId, ownerId);
        List<SchemaVersionEntity> versions = schemaVersionMapper.selectBySchemaId(schemaId);
        for (SchemaVersionEntity version : versions) {
            version.setOwnerId(parent.getOwnerId());
        }
        return versions;
    }

    public SchemaVersionEntity getVersion(Long schemaId, Long versionId, Long ownerId) {
        LabelSchemaEntity parent = assertSchemaOwnership(schemaId, ownerId);
        SchemaVersionEntity version = schemaVersionMapper.selectById(versionId);
        if (version == null || !Objects.equals(version.getSchemaId(), schemaId)) {
            throw new SchemaVersionNotFoundException(versionId);
        }
        version.setOwnerId(parent.getOwnerId());
        return version;
    }

    public SubmissionRenderSchemaView renderForSubmission(Long submissionId, Long requesterUserId) {
        return renderForSubmission(submissionId, requesterUserId, Set.of());
    }

    public SubmissionRenderSchemaView renderForSubmission(Long submissionId, Long requesterUserId, Set<String> requesterRoles) {
        SubmissionEntity submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new SubmissionNotFoundException(submissionId);
        }
        TaskEntity task = taskMapper.selectById(submission.getTaskId());
        boolean isLabeler = Objects.equals(submission.getLabelerId(), requesterUserId);
        boolean isOwner = task != null && Objects.equals(task.getOwnerId(), requesterUserId);
        boolean isReviewer = hasRole(requesterRoles, "REVIEWER");
        if (!isLabeler && !isOwner && !isReviewer) {
            throw new SubmissionNotFoundException(submissionId);
        }
        SchemaVersionEntity version = schemaVersionMapper.selectById(submission.getSchemaVersionId());
        if (version == null) {
            throw new SchemaVersionNotFoundException(submission.getSchemaVersionId());
        }
        LabelSchemaEntity parent = labelSchemaMapper.selectById(version.getSchemaId());
        if (parent == null) {
            throw new SchemaNotFoundException(version.getSchemaId());
        }
        version.setOwnerId(parent.getOwnerId());

        SubmissionRenderSchemaView view = new SubmissionRenderSchemaView();
        view.setSubmissionId(submissionId);
        view.setSchemaVersion(version);
        view.setAnswerPayload(submission.getAnswerPayload());
        view.setProvenance(submission.getProvenance());
        return view;
    }

    private boolean hasRole(Set<String> roles, String role) {
        return roles != null && (roles.contains(role) || roles.contains("ROLE_" + role));
    }

    private LabelSchemaEntity assertSchemaOwnership(Long schemaId, Long ownerId) {
        LabelSchemaEntity parent = labelSchemaMapper.selectById(schemaId);
        if (parent == null) {
            throw new SchemaNotFoundException(schemaId);
        }
        if (!Objects.equals(parent.getOwnerId(), ownerId)) {
            throw new SchemaAccessDeniedException(schemaId, ownerId);
        }
        return parent;
    }

    private void requireOneRow(int affectedRows, String action) {
        if (affectedRows != 1) {
            throw new IllegalStateException("Expected one row for " + action + " but got " + affectedRows);
        }
    }
}
