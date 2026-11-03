package com.labelhub.api.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelhub.api.module.admin.audit.AuditLogService;
import com.labelhub.api.module.ai.entity.AiReviewAutoPolicyEventEntity;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.exception.AutoPolicyGateException;
import com.labelhub.api.module.ai.exception.InvalidAiReviewRuleException;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.mapper.AiCallMapper.ShadowStatsRow;
import com.labelhub.api.module.ai.mapper.AiReviewAutoPolicyEventMapper;
import com.labelhub.api.module.ai.mapper.AiReviewAutoPolicyEventMapper.SampleStatsRow;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ADR-020 §3: the auto-pass gate, the append-only event log, and the circuit breaker. */
class AutoPolicyServiceTest {

    private static final Long RULE = 19L;
    private static final Long TASK = 44L;
    private static final Long OWNER = 1001L;
    private static final Long PROMPT = 7L;
    private static final String MODEL = "gpt-6.1-terra";

    private final AiReviewRuleMapper ruleMapper = mock(AiReviewRuleMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AiCallMapper aiCallMapper = mock(AiCallMapper.class);
    private final AiReviewAutoPolicyEventMapper eventMapper = mock(AiReviewAutoPolicyEventMapper.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final List<AiReviewAutoPolicyEventEntity> log = new ArrayList<>();
    private AutoPolicyService service;

    @BeforeEach
    void setUp() {
        service = new AutoPolicyService(ruleMapper, taskMapper, aiCallMapper, eventMapper, audit,
            Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC));
        AiReviewRuleEntity rule = new AiReviewRuleEntity();
        rule.setId(RULE);
        rule.setTaskId(TASK);
        rule.setCurrentPromptVersionId(PROMPT);
        rule.setStatusCode("published");
        when(ruleMapper.selectById(RULE)).thenReturn(rule);
        TaskEntity task = new TaskEntity();
        task.setId(TASK);
        task.setOwnerId(OWNER);
        task.setCurrentAiReviewRuleId(RULE);
        when(taskMapper.selectById(TASK)).thenReturn(task);
        // In-memory append-only log behind the mapper.
        when(eventMapper.insert(any())).thenAnswer(inv -> {
            AiReviewAutoPolicyEventEntity e = inv.getArgument(0);
            e.setId(100L + log.size());
            log.add(e);
            return 1;
        });
        when(eventMapper.selectLatestByRuleId(RULE)).thenAnswer(inv -> log.isEmpty() ? null : log.get(log.size() - 1));
        when(eventMapper.selectByRuleIdOrdered(RULE)).thenAnswer(inv -> List.copyOf(log));
        when(eventMapper.selectById(any())).thenAnswer(inv -> log.stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(eventMapper.selectSampleStats(any())).thenReturn(new SampleStatsRow(0L, 0L, 0L));
    }

    private AutoPolicyService.EnableCommand cmd(String maxError) {
        return new AutoPolicyService.EnableCommand(PROMPT, MODEL, new BigDecimal(maxError), null, null);
    }

    private void shadow(long judged, long rejected) {
        when(aiCallMapper.selectShadowStatsByRuleId(RULE)).thenReturn(List.of(new ShadowStatsRow(PROMPT, MODEL, judged, rejected)));
    }

    @Test
    void enable_succeeds_only_when_shadow_upper_bound_is_within_max_error_and_snapshots_the_evidence() {
        shadow(400, 4);  // 1% observed, Wilson upper 0.025

        AutoPolicyService.State state = service.enable(RULE, OWNER, cmd("0.03"));

        assertThat(state.mode()).isEqualTo("auto");
        AiReviewAutoPolicyEventEntity enable = log.get(0);
        assertThat(enable.getAction()).isEqualTo("enable");
        assertThat(enable.getActorType()).isEqualTo("owner");
        assertThat(enable.getActorId()).isEqualTo(OWNER);
        assertThat(enable.getModelName()).isEqualTo(MODEL);
        assertThat(enable.getMinSamples()).isEqualTo(200);
        assertThat(enable.getSampleRate()).isEqualByComparingTo("0.10");
        assertThat(enable.getEvidence()).containsEntry("aiPassJudged", 400L).containsEntry("humanRejected", 4L);
        verify(audit).record(any());
    }

    @Test
    void the_w1_baseline_could_not_enable_auto_at_any_sensible_cap() {
        shadow(280, 25);  // 8.93% observed, Wilson upper 12.8%
        assertThatThrownBy(() -> service.enable(RULE, OWNER, cmd("0.10")))
            .isInstanceOf(AutoPolicyGateException.class)
            .hasMessageContaining("0.128480");
        assertThat(log).isEmpty();
    }

    @Test
    void enable_refuses_too_few_samples_even_with_zero_errors() {
        shadow(199, 0);
        assertThatThrownBy(() -> service.enable(RULE, OWNER, cmd("0.05")))
            .isInstanceOf(AutoPolicyGateException.class).hasMessageContaining("199");
    }

    @Test
    void evidence_from_another_model_or_prompt_does_not_count() {
        when(aiCallMapper.selectShadowStatsByRuleId(RULE)).thenReturn(List.of(new ShadowStatsRow(PROMPT, "other-model", 1000L, 0L)));
        assertThatThrownBy(() -> service.enable(RULE, OWNER, cmd("0.05"))).isInstanceOf(AutoPolicyGateException.class);
        assertThatThrownBy(() -> service.enable(RULE, OWNER, new AutoPolicyService.EnableCommand(8L, MODEL, new BigDecimal("0.05"), null, null)))
            .isInstanceOf(AutoPolicyGateException.class).hasMessageContaining("prompt");
    }

    @Test
    void only_the_tasks_current_published_rule_can_go_auto() {
        shadow(1000, 0);
        TaskEntity task = taskMapper.selectById(TASK);
        task.setCurrentAiReviewRuleId(20L);
        assertThatThrownBy(() -> service.enable(RULE, OWNER, cmd("0.05"))).isInstanceOf(AutoPolicyGateException.class);
    }

    @Test
    void parameters_are_validated_and_cross_owner_is_not_found() {
        shadow(1000, 0);
        assertThatThrownBy(() -> service.enable(RULE, OWNER, cmd("0"))).isInstanceOf(InvalidAiReviewRuleException.class);
        assertThatThrownBy(() -> service.enable(RULE, OWNER, cmd("0.6"))).isInstanceOf(InvalidAiReviewRuleException.class);
        assertThatThrownBy(() -> service.enable(RULE, OWNER, new AutoPolicyService.EnableCommand(PROMPT, MODEL, new BigDecimal("0.05"), 100, null)))
            .isInstanceOf(InvalidAiReviewRuleException.class);
        assertThatThrownBy(() -> service.enable(RULE, OWNER, new AutoPolicyService.EnableCommand(PROMPT, MODEL, new BigDecimal("0.05"), null, new BigDecimal("0.01"))))
            .isInstanceOf(InvalidAiReviewRuleException.class);
        assertThatThrownBy(() -> service.enable(RULE, 2002L, cmd("0.05"))).isInstanceOf(AiReviewRuleNotFoundException.class);
    }

    @Test
    void disable_appends_an_event_and_is_idempotent() {
        shadow(1000, 0);
        service.enable(RULE, OWNER, cmd("0.05"));

        assertThat(service.disable(RULE, OWNER, "pause").mode()).isEqualTo("suggest");
        assertThat(service.disable(RULE, OWNER, "again").mode()).isEqualTo("suggest");
        assertThat(log).extracting(AiReviewAutoPolicyEventEntity::getAction).containsExactly("enable", "disable");
        assertThat(service.activeEnable(RULE)).isNull();
    }

    @Test
    void breaker_trips_on_significant_sampled_error_and_goes_back_to_suggest() {
        shadow(1000, 0);
        service.enable(RULE, OWNER, cmd("0.05"));
        Long enableId = log.get(0).getId();
        when(eventMapper.selectSampleStats(enableId)).thenReturn(new SampleStatsRow(12L, 10L, 3L));  // lower bound ~0.108 > 0.05

        assertThat(service.evaluateCircuitBreaker(enableId)).isTrue();

        AiReviewAutoPolicyEventEntity breaker = log.get(1);
        assertThat(breaker.getAction()).isEqualTo("circuit_break");
        assertThat(breaker.getActorType()).isEqualTo("system");
        assertThat(breaker.getEvidence()).containsEntry("sampleJudged", 10L).containsEntry("sampleRejected", 3L);
        assertThat(service.activeEnable(RULE)).isNull();
        assertThat(service.evaluateCircuitBreaker(enableId)).isFalse();  // stale event: no second trip
    }

    @Test
    void breaker_does_not_trip_on_a_small_unlucky_sample() {
        shadow(1000, 0);
        service.enable(RULE, OWNER, cmd("0.05"));
        Long enableId = log.get(0).getId();
        // The upper bound of 0/1 is ~0.79; a breaker on the upper bound would trip on the first clean sample.
        when(eventMapper.selectSampleStats(enableId)).thenReturn(new SampleStatsRow(1L, 1L, 0L));
        assertThat(service.evaluateCircuitBreaker(enableId)).isFalse();
        when(eventMapper.selectSampleStats(enableId)).thenReturn(new SampleStatsRow(40L, 40L, 1L));  // 2.5% observed, lower ~0.004
        assertThat(service.evaluateCircuitBreaker(enableId)).isFalse();
        verify(eventMapper, never()).insert(org.mockito.ArgumentMatchers.argThat(e -> "circuit_break".equals(e.getAction())));
    }

    @Test
    void wilson_lower_bound_values() {
        assertThat(WilsonInterval.lower95(3, 10)).isGreaterThan(new BigDecimal("0.05"));
        assertThat(WilsonInterval.lower95(0, 50)).isEqualByComparingTo("0");
        assertThat(WilsonInterval.lower95(0, 0)).isNull();
    }
}
