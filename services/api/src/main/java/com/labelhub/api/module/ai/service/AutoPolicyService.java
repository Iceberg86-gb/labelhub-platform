package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.admin.audit.AuditActions;
import com.labelhub.api.module.admin.audit.AuditEventBuilder;
import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.ai.entity.AiReviewAutoPolicyEventEntity;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.exception.AutoPolicyGateException;
import com.labelhub.api.module.ai.exception.InvalidAiReviewRuleException;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.mapper.AiReviewAutoPolicyEventMapper;
import com.labelhub.api.module.ai.mapper.AiReviewAutoPolicyEventMapper.SampleStatsRow;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-020 §3 (W3b): the auto-pass policy of a rule version — gate, enable/disable, state and circuit breaker.
 *
 * <p>The policy is an append-only event log; the current mode is the latest event (enable = auto). The gate only
 * accepts the system's own shadow evidence for the exact (rule, prompt version, model).
 */
@Service
public class AutoPolicyService {

    public static final int MIN_SAMPLES_FLOOR = 200;
    public static final BigDecimal DEFAULT_SAMPLE_RATE = new BigDecimal("0.10");
    public static final BigDecimal SAMPLE_RATE_FLOOR = new BigDecimal("0.05");
    public static final BigDecimal MAX_ERROR_CEILING = new BigDecimal("0.50");

    public record EnableCommand(Long promptVersionId, String modelName, BigDecimal maxError, Integer minSamples, BigDecimal sampleRate) {
    }

    public record SampleStats(long sampled, long judged, long humanRejected, BigDecimal wilsonLower95, BigDecimal wilsonUpper95, long autoAccepted) {
    }

    public record State(Long ruleId, String mode, AiReviewAutoPolicyEventEntity current, List<AiReviewAutoPolicyEventEntity> events, SampleStats samples) {
    }

    private final AiReviewRuleMapper aiReviewRuleMapper;
    private final TaskMapper taskMapper;
    private final AiCallMapper aiCallMapper;
    private final AiReviewAutoPolicyEventMapper eventMapper;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AutoPolicyService(
        AiReviewRuleMapper aiReviewRuleMapper,
        TaskMapper taskMapper,
        AiCallMapper aiCallMapper,
        AiReviewAutoPolicyEventMapper eventMapper,
        AuditLogService auditLogService,
        Clock clock
    ) {
        this.aiReviewRuleMapper = aiReviewRuleMapper;
        this.taskMapper = taskMapper;
        this.aiCallMapper = aiCallMapper;
        this.eventMapper = eventMapper;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** The enable event currently in force for a rule version, or null (suggest mode). */
    public AiReviewAutoPolicyEventEntity activeEnable(Long ruleId) {
        if (ruleId == null) {
            return null;
        }
        AiReviewAutoPolicyEventEntity latest = eventMapper.selectLatestByRuleId(ruleId);
        return latest != null && latest.isEnable() ? latest : null;
    }

    public State state(Long ruleId, Long ownerId) {
        AiReviewRuleEntity rule = requireOwnedRule(ruleId, ownerId);
        return state(rule.getId());
    }

    @Transactional
    public State enable(Long ruleId, Long ownerId, EnableCommand command) {
        AiReviewRuleEntity rule = requireOwnedRule(ruleId, ownerId);
        TaskEntity task = taskMapper.selectById(rule.getTaskId());
        if (!"published".equals(rule.getStatusCode()) || task == null || !Objects.equals(task.getCurrentAiReviewRuleId(), rule.getId())) {
            throw new AutoPolicyGateException("只有任务当前生效的已发布规则版本可以开启自动通过");
        }
        BigDecimal maxError = command.maxError();
        if (maxError == null || maxError.signum() <= 0 || maxError.compareTo(MAX_ERROR_CEILING) > 0) {
            throw new InvalidAiReviewRuleException("maxError", "自动通过错误率上限必须在 (0, 0.5] 之间");
        }
        int minSamples = command.minSamples() == null ? MIN_SAMPLES_FLOOR : command.minSamples();
        if (minSamples < MIN_SAMPLES_FLOOR) {
            throw new InvalidAiReviewRuleException("minSamples", "最少样本数不能低于 " + MIN_SAMPLES_FLOOR);
        }
        BigDecimal sampleRate = command.sampleRate() == null ? DEFAULT_SAMPLE_RATE : command.sampleRate();
        if (sampleRate.compareTo(SAMPLE_RATE_FLOOR) < 0 || sampleRate.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidAiReviewRuleException("sampleRate", "抽检比例必须在 [0.05, 1] 之间");
        }
        if (!Objects.equals(command.promptVersionId(), rule.getCurrentPromptVersionId())) {
            throw new AutoPolicyGateException("门槛证据的 prompt 版本必须是该规则版本的 prompt");
        }
        if (command.modelName() == null || command.modelName().isBlank()) {
            throw new InvalidAiReviewRuleException("modelName", "必须指定门槛证据对应的模型");
        }
        AiCallMapper.ShadowStatsRow evidence = aiCallMapper.selectShadowStatsByRuleId(ruleId).stream()
            .filter(row -> Objects.equals(row.getPromptVersionId(), command.promptVersionId()) && command.modelName().equals(row.getModelName()))
            .findFirst()
            .orElse(null);
        long judged = evidence == null || evidence.getJudged() == null ? 0 : evidence.getJudged();
        long rejected = evidence == null || evidence.getRejected() == null ? 0 : evidence.getRejected();
        if (judged < minSamples) {
            throw new AutoPolicyGateException("影子样本不足：AI 判通过且已有人工裁决的提交只有 " + judged + " 条，至少需要 " + minSamples + " 条");
        }
        BigDecimal upper = WilsonInterval.upper95(rejected, judged);
        if (upper.compareTo(maxError) > 0) {
            throw new AutoPolicyGateException("影子错误率的 95% 置信上界为 " + upper.toPlainString() + "，超过上限 " + maxError.toPlainString()
                + "（" + rejected + "/" + judged + " 被人工打回）");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("aiPassJudged", judged);
        snapshot.put("humanRejected", rejected);
        snapshot.put("wilsonUpper95", upper);
        snapshot.put("measuredAt", LocalDateTime.now(clock).toString());
        AiReviewAutoPolicyEventEntity event = event(rule, AiReviewAutoPolicyEventEntity.ENABLE, "owner", ownerId, null, snapshot);
        event.setPromptVersionId(command.promptVersionId());
        event.setModelName(command.modelName());
        event.setMaxError(maxError);
        event.setMinSamples(minSamples);
        event.setSampleRate(sampleRate);
        insert(event);
        auditLogService.record(AuditEventBuilder.forAction(AuditActions.AI_AUTO_POLICY_ENABLE)
            .actorUser(ownerId)
            .resource("ai_review_rule", rule.getId())
            .payload("policyEventId", event.getId())
            .payload("taskId", rule.getTaskId())
            .payload("promptVersionId", command.promptVersionId())
            .payload("modelName", command.modelName())
            .payload("maxError", maxError)
            .payload("sampleRate", sampleRate)
            .payload("evidence", snapshot));
        return state(rule.getId());
    }

    @Transactional
    public State disable(Long ruleId, Long ownerId, String reason) {
        AiReviewRuleEntity rule = requireOwnedRule(ruleId, ownerId);
        if (activeEnable(rule.getId()) == null) {
            return state(rule.getId());  // already suggest: idempotent, no event
        }
        AiReviewAutoPolicyEventEntity event = event(rule, AiReviewAutoPolicyEventEntity.DISABLE, "owner", ownerId, reason, Map.of());
        insert(event);
        auditLogService.record(AuditEventBuilder.forAction(AuditActions.AI_AUTO_POLICY_DISABLE)
            .actorUser(ownerId)
            .resource("ai_review_rule", rule.getId())
            .payload("policyEventId", event.getId())
            .payload("reason", reason == null ? "" : reason));
        return state(rule.getId());
    }

    /**
     * Re-evaluate the breaker for an enable event after one of its samples got a human verdict. Trips (appends a
     * circuit_break event, back to suggest) when the Wilson 95% lower bound of the sampled error exceeds max_error.
     * Returns true when it tripped. No-op if the event is no longer the one in force.
     */
    @Transactional
    public boolean evaluateCircuitBreaker(Long policyEventId) {
        AiReviewAutoPolicyEventEntity enable = eventMapper.selectById(policyEventId);
        if (enable == null || !enable.isEnable()) {
            return false;
        }
        AiReviewAutoPolicyEventEntity active = activeEnable(enable.getAiReviewRuleId());
        if (active == null || !Objects.equals(active.getId(), enable.getId())) {
            return false;
        }
        SampleStatsRow stats = eventMapper.selectSampleStats(enable.getId());
        long judged = stats == null ? 0 : stats.judgedOrZero();
        long rejected = stats == null ? 0 : stats.rejectedOrZero();
        BigDecimal lower = WilsonInterval.lower95(rejected, judged);
        if (lower == null || lower.compareTo(enable.getMaxError()) <= 0) {
            return false;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("policyEventId", enable.getId());
        evidence.put("sampleJudged", judged);
        evidence.put("sampleRejected", rejected);
        evidence.put("wilsonLower95", lower);
        evidence.put("maxError", enable.getMaxError());
        AiReviewRuleEntity rule = aiReviewRuleMapper.selectById(enable.getAiReviewRuleId());
        AiReviewAutoPolicyEventEntity breaker = event(rule, AiReviewAutoPolicyEventEntity.CIRCUIT_BREAK, "system", null,
            "抽检错误率的 95% 置信下界 " + lower.toPlainString() + " 超过上限 " + enable.getMaxError().toPlainString(), evidence);
        insert(breaker);
        auditLogService.record(AuditEventBuilder.forAction(AuditActions.AI_AUTO_POLICY_CIRCUIT_BREAK)
            .actorSystem()
            .resource("ai_review_rule", rule.getId())
            .payload("policyEventId", breaker.getId())
            .payload(evidence));
        return true;
    }

    private State state(Long ruleId) {
        List<AiReviewAutoPolicyEventEntity> events = eventMapper.selectByRuleIdOrdered(ruleId);
        AiReviewAutoPolicyEventEntity latest = events.isEmpty() ? null : events.get(events.size() - 1);
        AiReviewAutoPolicyEventEntity lastEnable = null;
        for (AiReviewAutoPolicyEventEntity event : events) {
            if (event.isEnable()) {
                lastEnable = event;
            }
        }
        SampleStats samples = null;
        if (lastEnable != null) {
            SampleStatsRow row = eventMapper.selectSampleStats(lastEnable.getId());
            long judged = row == null ? 0 : row.judgedOrZero();
            long rejected = row == null ? 0 : row.rejectedOrZero();
            Long accepted = eventMapper.selectAutoAcceptedCount(lastEnable.getId());
            samples = new SampleStats(row == null ? 0 : row.sampledOrZero(), judged, rejected,
                WilsonInterval.lower95(rejected, judged), WilsonInterval.upper95(rejected, judged), accepted == null ? 0 : accepted);
        }
        String mode = latest != null && latest.isEnable() ? "auto" : "suggest";
        return new State(ruleId, mode, latest, events, samples);
    }

    private AiReviewRuleEntity requireOwnedRule(Long ruleId, Long ownerId) {
        AiReviewRuleEntity rule = aiReviewRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new AiReviewRuleNotFoundException(ruleId);
        }
        TaskEntity task = taskMapper.selectById(rule.getTaskId());
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new AiReviewRuleNotFoundException(ruleId);
        }
        return rule;
    }

    private AiReviewAutoPolicyEventEntity event(AiReviewRuleEntity rule, String action, String actorType, Long actorId, String reason, Map<String, Object> evidence) {
        AiReviewAutoPolicyEventEntity event = new AiReviewAutoPolicyEventEntity();
        event.setAiReviewRuleId(rule.getId());
        event.setTaskId(rule.getTaskId());
        event.setAction(action);
        event.setEvidence(evidence);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setReason(reason);
        event.setCreatedAt(LocalDateTime.now(clock));
        return event;
    }

    private void insert(AiReviewAutoPolicyEventEntity event) {
        if (eventMapper.insert(event) != 1) {
            throw new IllegalStateException("Expected one row for insert auto policy event");
        }
    }
}
