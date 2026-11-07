package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * ADR-020 §3 (W3a, read-only): shadow auto-pass error of a rule version, measured on production data in suggest
 * mode. This is the only evidence the W3b auto gate will accept.
 */
@Service
public class AiReviewShadowStatsService {

    public record Group(Long promptVersionId, String modelName, long aiPassJudged, long humanRejected, BigDecimal errorRate, BigDecimal wilsonUpper95) {
    }

    public record View(Long ruleId, List<Group> groups) {
    }

    private final AiReviewRuleMapper aiReviewRuleMapper;
    private final TaskMapper taskMapper;
    private final AiCallMapper aiCallMapper;

    private com.labelhub.api.module.ai.precedent.PrecedentProperties precedentProperties;

    /** ADR-021 §3: shadow evidence is scoped to the adapter version (with/without precedent memory). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setPrecedentProperties(com.labelhub.api.module.ai.precedent.PrecedentProperties precedentProperties) {
        this.precedentProperties = precedentProperties;
    }

    String currentAdapterVersion() {
        return precedentProperties == null
            ? com.labelhub.api.module.ai.precedent.PrecedentProperties.BASE_ADAPTER_VERSION
            : precedentProperties.adapterVersion();
    }

    public AiReviewShadowStatsService(AiReviewRuleMapper aiReviewRuleMapper, TaskMapper taskMapper, AiCallMapper aiCallMapper) {
        this.aiReviewRuleMapper = aiReviewRuleMapper;
        this.taskMapper = taskMapper;
        this.aiCallMapper = aiCallMapper;
    }

    public View shadowStats(Long ruleId, Long ownerId) {
        AiReviewRuleEntity rule = aiReviewRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new AiReviewRuleNotFoundException(ruleId);
        }
        TaskEntity task = taskMapper.selectById(rule.getTaskId());
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new AiReviewRuleNotFoundException(ruleId);  // same 404 for missing and cross-owner, like the other rule endpoints
        }
        List<Group> groups = aiCallMapper.selectShadowStatsByRuleId(ruleId, currentAdapterVersion()).stream()
            .map(row -> {
                long judged = row.getJudged() == null ? 0 : row.getJudged();
                long rejected = row.getRejected() == null ? 0 : row.getRejected();
                BigDecimal rate = judged == 0 ? null : BigDecimal.valueOf(rejected).divide(BigDecimal.valueOf(judged), 6, RoundingMode.HALF_UP);
                return new Group(row.getPromptVersionId(), row.getModelName(), judged, rejected, rate, WilsonInterval.upper95(rejected, judged));
            })
            .toList();
        return new View(ruleId, groups);
    }
}
