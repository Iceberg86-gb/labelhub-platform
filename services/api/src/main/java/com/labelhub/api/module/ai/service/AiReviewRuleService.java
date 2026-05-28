package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.AiReviewRuleRequest;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.exception.InvalidAiReviewRuleException;
import com.labelhub.api.module.ai.exception.PromptVersionNotFoundException;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.ai.service.view.AiReviewRuleView;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiReviewRuleService {

    private static final int MAX_INSERT_ATTEMPTS = 3;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final AiReviewRuleMapper aiReviewRuleMapper;
    private final PromptVersionService promptVersionService;
    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    public AiReviewRuleService(
        AiReviewRuleMapper aiReviewRuleMapper,
        PromptVersionService promptVersionService,
        TaskMapper taskMapper,
        ObjectMapper objectMapper
    ) {
        this.aiReviewRuleMapper = aiReviewRuleMapper;
        this.promptVersionService = promptVersionService;
        this.taskMapper = taskMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiReviewRuleView saveRule(AiReviewRuleRequest request, Long ownerId) {
        List<String> dimensions = validateDimensions(request.getDimensions());
        validatePromptTemplate(request.getPromptTemplate());
        validateThreshold(request.getThreshold());
        TaskEntity task = requireOwnedTask(request.getTaskId(), ownerId);
        PromptVersionEntity promptVersion = promptVersionService.create(request.getPromptTemplate(), ownerId);

        AiReviewRuleEntity entity = new AiReviewRuleEntity();
        entity.setTaskId(task.getId());
        entity.setCurrentPromptVersionId(promptVersion.getId());
        entity.setDimensionsJson(writeDimensions(dimensions));
        entity.setThreshold(request.getThreshold());
        entity.setStatusCode("draft");
        entity.setCreatedBy(ownerId);

        insertWithVersionRetry(entity);
        return new AiReviewRuleView(entity, promptVersion);
    }

    @Transactional
    public AiReviewRuleView publishRule(Long ruleId, Long ownerId) {
        AiReviewRuleEntity rule = aiReviewRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new AiReviewRuleNotFoundException(ruleId);
        }
        requireOwnedRuleTask(rule, ownerId);
        if (!"published".equals(rule.getStatusCode())) {
            aiReviewRuleMapper.markPublished(ruleId);
            rule = aiReviewRuleMapper.selectById(ruleId);
            if (rule == null) {
                throw new AiReviewRuleNotFoundException(ruleId);
            }
        }
        taskMapper.updateCurrentAiReviewRuleId(rule.getTaskId(), rule.getId());
        PromptVersionEntity promptVersion = promptVersionService.findById(rule.getCurrentPromptVersionId());
        if (promptVersion == null) {
            throw new PromptVersionNotFoundException(rule.getCurrentPromptVersionId());
        }
        return new AiReviewRuleView(rule, promptVersion);
    }

    private void insertWithVersionRetry(AiReviewRuleEntity entity) {
        for (int attempt = 0; attempt < MAX_INSERT_ATTEMPTS; attempt++) {
            Integer maxVersion = aiReviewRuleMapper.selectMaxVersionByTaskId(entity.getTaskId());
            entity.setVersionNumber((maxVersion == null ? 0 : maxVersion) + 1);
            try {
                aiReviewRuleMapper.insert(entity);
                return;
            } catch (DuplicateKeyException exception) {
                if (attempt == MAX_INSERT_ATTEMPTS - 1) {
                    throw exception;
                }
            }
        }
    }

    private TaskEntity requireOwnedTask(Long taskId, Long ownerId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(taskId);
        }
        return task;
    }

    private void requireOwnedRuleTask(AiReviewRuleEntity rule, Long ownerId) {
        TaskEntity task = taskMapper.selectById(rule.getTaskId());
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new AiReviewRuleNotFoundException(rule.getId());
        }
    }

    private void validatePromptTemplate(String promptTemplate) {
        if (promptTemplate == null || promptTemplate.trim().isEmpty()) {
            throw new InvalidAiReviewRuleException("promptTemplate", "Prompt 模板不能为空");
        }
    }

    private List<String> validateDimensions(List<String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            throw new InvalidAiReviewRuleException("dimensions", "评分维度不能为空");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String dimension : dimensions) {
            String value = dimension == null ? "" : dimension.trim();
            if (value.isEmpty()) {
                throw new InvalidAiReviewRuleException("dimensions", "评分维度不能为空");
            }
            if (!seen.add(value)) {
                throw new InvalidAiReviewRuleException("dimensions", "评分维度不能重复");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private void validateThreshold(BigDecimal threshold) {
        if (threshold == null || threshold.compareTo(ZERO) < 0 || threshold.compareTo(ONE) > 0) {
            throw new InvalidAiReviewRuleException("threshold", "阈值必须在 0 到 1 之间");
        }
    }

    private String writeDimensions(List<String> dimensions) {
        try {
            return objectMapper.writeValueAsString(dimensions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize AI review rule dimensions", exception);
        }
    }
}
