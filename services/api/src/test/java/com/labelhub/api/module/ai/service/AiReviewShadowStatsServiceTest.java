package com.labelhub.api.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.mapper.AiCallMapper;
import com.labelhub.api.module.ai.mapper.AiCallMapper.ShadowStatsRow;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR-020 §3: the shadow error the W3b auto gate will compare, and its Wilson bound. */
class AiReviewShadowStatsServiceTest {

    private final AiReviewRuleMapper ruleMapper = mock(AiReviewRuleMapper.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AiCallMapper aiCallMapper = mock(AiCallMapper.class);
    private final AiReviewShadowStatsService service = new AiReviewShadowStatsService(ruleMapper, taskMapper, aiCallMapper);

    @Test
    void groups_by_prompt_and_model_with_error_rate_and_wilson_upper_bound() {
        ownedRule(19L, 44L, 1001L);
        when(aiCallMapper.selectShadowStatsByRuleId(19L)).thenReturn(List.of(
            new ShadowStatsRow(7L, "gpt-6.1-terra", 200L, 0L),
            new ShadowStatsRow(7L, "gpt-6.1-sol", 280L, 25L)
        ));

        AiReviewShadowStatsService.View view = service.shadowStats(19L, 1001L);

        assertThat(view.groups()).hasSize(2);
        AiReviewShadowStatsService.Group clean = view.groups().get(0);
        assertThat(clean.errorRate()).isEqualByComparingTo("0");
        assertThat(clean.wilsonUpper95()).isEqualByComparingTo("0.018845");  // zero errors in 200 is not "0% error"
        AiReviewShadowStatsService.Group w1 = view.groups().get(1);
        assertThat(w1.errorRate()).isEqualByComparingTo("0.089286");  // the W1 baseline's auto-pass error, 25/280
        assertThat(w1.wilsonUpper95()).isEqualByComparingTo("0.128480");
    }

    @Test
    void cross_owner_and_missing_rules_are_both_not_found() {
        ownedRule(19L, 44L, 1001L);
        assertThatThrownBy(() -> service.shadowStats(19L, 2002L)).isInstanceOf(AiReviewRuleNotFoundException.class);
        assertThatThrownBy(() -> service.shadowStats(404L, 1001L)).isInstanceOf(AiReviewRuleNotFoundException.class);
        verify(aiCallMapper, never()).selectShadowStatsByRuleId(19L);
    }

    @Test
    void wilson_bound_edge_cases() {
        assertThat(WilsonInterval.upper95(0, 0)).isNull();
        assertThat(WilsonInterval.upper95(10, 10)).isEqualByComparingTo("1.000000");
        assertThat(WilsonInterval.upper95(1, 100)).isEqualByComparingTo(new BigDecimal("0.054486"));
        assertThatThrownBy(() -> WilsonInterval.upper95(3, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    private void ownedRule(Long ruleId, Long taskId, Long ownerId) {
        AiReviewRuleEntity rule = new AiReviewRuleEntity();
        rule.setId(ruleId);
        rule.setTaskId(taskId);
        when(ruleMapper.selectById(ruleId)).thenReturn(rule);
        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setOwnerId(ownerId);
        when(taskMapper.selectById(taskId)).thenReturn(task);
    }
}
