package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.AiReviewRuleRequest;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.exception.InvalidAiReviewRuleException;
import com.labelhub.api.module.ai.exception.PromptVersionNotFoundException;
import com.labelhub.api.module.ai.mapper.AiReviewRuleMapper;
import com.labelhub.api.module.ai.service.view.AiReviewRuleView;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AiReviewRuleServiceTest {

    private final AiReviewRuleMapper aiReviewRuleMapper = mock(AiReviewRuleMapper.class);
    private final PromptVersionService promptVersionService = mock(PromptVersionService.class);
    private final TaskMapper taskMapper = mock(TaskMapper.class);
    private final AiReviewRuleService service = new AiReviewRuleService(
        aiReviewRuleMapper,
        promptVersionService,
        taskMapper,
        new ObjectMapper()
    );

    @Test
    void saveRule_creates_draft_rule_version_pointing_to_prompt_version() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L));
        PromptVersionEntity promptVersion = promptVersion(7L, "Review prompt");
        when(promptVersionService.create("Review prompt", 1001L)).thenReturn(promptVersion);
        when(aiReviewRuleMapper.selectMaxVersionByTaskId(44L)).thenReturn(2);
        when(aiReviewRuleMapper.insert(any())).thenAnswer(invocation -> {
            AiReviewRuleEntity entity = invocation.getArgument(0);
            entity.setId(19L);
            return 1;
        });

        AiReviewRuleView result = service.saveRule(request(44L, "Review prompt", List.of("accuracy", "safety"), "0.8000"), 1001L);

        assertThat(result.rule().getId()).isEqualTo(19L);
        assertThat(result.promptVersion()).isSameAs(promptVersion);
        assertThat(result.rule().getTaskId()).isEqualTo(44L);
        assertThat(result.rule().getVersionNumber()).isEqualTo(3);
        assertThat(result.rule().getCurrentPromptVersionId()).isEqualTo(7L);
        assertThat(result.rule().getDimensionsJson()).isEqualTo("[\"accuracy\",\"safety\"]");
        assertThat(result.rule().getThreshold()).isEqualByComparingTo("0.8000");
        assertThat(result.rule().getStatusCode()).isEqualTo("draft");
        assertThat(result.rule().getCreatedBy()).isEqualTo(1001L);
        assertThat(result.isCurrent()).isFalse();
    }

    @Test
    void saveRule_reuses_prompt_version_and_creates_new_rule_version_for_changed_threshold() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L));
        PromptVersionEntity promptVersion = promptVersion(7L, "Review prompt");
        when(promptVersionService.create("Review prompt", 1001L)).thenReturn(promptVersion);
        when(aiReviewRuleMapper.selectMaxVersionByTaskId(44L)).thenReturn(3);
        when(aiReviewRuleMapper.insert(any())).thenAnswer(invocation -> {
            AiReviewRuleEntity entity = invocation.getArgument(0);
            entity.setId(20L);
            return 1;
        });

        AiReviewRuleView result = service.saveRule(request(44L, "Review prompt", List.of("accuracy"), "0.9000"), 1001L);

        assertThat(result.rule().getVersionNumber()).isEqualTo(4);
        assertThat(result.rule().getCurrentPromptVersionId()).isEqualTo(7L);
        assertThat(result.rule().getThreshold()).isEqualByComparingTo("0.9000");
        verify(promptVersionService).create("Review prompt", 1001L);
    }

    @Test
    void saveRule_retries_task_scoped_version_collision_before_insert_succeeds() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L));
        when(promptVersionService.create("Review prompt", 1001L)).thenReturn(promptVersion(7L, "Review prompt"));
        when(aiReviewRuleMapper.selectMaxVersionByTaskId(44L)).thenReturn(2, 3);
        when(aiReviewRuleMapper.insert(any()))
            .thenThrow(new DuplicateKeyException("uk_ai_review_rules_task_version"))
            .thenAnswer(invocation -> {
                AiReviewRuleEntity entity = invocation.getArgument(0);
                entity.setId(21L);
                return 1;
            });

        AiReviewRuleView result = service.saveRule(request(44L, "Review prompt", List.of("accuracy"), "0.8000"), 1001L);

        assertThat(result.rule().getId()).isEqualTo(21L);
        assertThat(result.rule().getVersionNumber()).isEqualTo(4);
    }

    @Test
    void saveRule_rejects_invalid_fields_before_creating_prompt_version() {
        assertThatThrownBy(() -> service.saveRule(request(44L, "   ", List.of("accuracy"), "0.8000"), 1001L))
            .isInstanceOf(InvalidAiReviewRuleException.class)
            .hasMessageContaining("Prompt 模板不能为空");

        assertThatThrownBy(() -> service.saveRule(request(44L, "Review prompt", List.of("accuracy", "accuracy"), "0.8000"), 1001L))
            .isInstanceOf(InvalidAiReviewRuleException.class)
            .hasMessageContaining("评分维度不能重复");

        assertThatThrownBy(() -> service.saveRule(request(44L, "Review prompt", List.of("accuracy"), "1.1000"), 1001L))
            .isInstanceOf(InvalidAiReviewRuleException.class)
            .hasMessageContaining("阈值必须在 0 到 1 之间");
    }

    @Test
    void saveRule_returns_not_found_for_missing_or_cross_owner_task() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 2002L));

        assertThatThrownBy(() -> service.saveRule(request(44L, "Review prompt", List.of("accuracy"), "0.8000"), 1001L))
            .isInstanceOf(TaskNotFoundException.class)
            .hasMessageContaining("Task not found");
    }

    @Test
    void publishRule_marks_rule_published_and_updates_task_pointer() {
        AiReviewRuleEntity rule = draftRule(19L, 44L, 7L, "draft");
        when(aiReviewRuleMapper.selectById(19L)).thenReturn(rule, publishedRule(19L, 44L, 7L));
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L));
        PromptVersionEntity promptVersion = promptVersion(7L, "Review prompt");
        when(promptVersionService.findById(7L)).thenReturn(promptVersion);

        AiReviewRuleView result = service.publishRule(19L, 1001L);

        verify(aiReviewRuleMapper).markPublished(19L);
        verify(taskMapper).updateCurrentAiReviewRuleId(44L, 19L);
        assertThat(result.rule().getStatusCode()).isEqualTo("published");
        assertThat(result.promptVersion()).isSameAs(promptVersion);
        assertThat(result.isCurrent()).isTrue();
    }

    @Test
    void listRules_returns_empty_list_for_owned_task_without_rules() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L, null));
        when(aiReviewRuleMapper.selectByTaskIdOrderByVersionAsc(44L)).thenReturn(List.of());

        List<AiReviewRuleView> result = service.listRules(44L, 1001L);

        assertThat(result).isEmpty();
        verify(promptVersionService, never()).findById(any());
    }

    @Test
    void listRules_returns_version_asc_and_marks_only_task_pointer_as_current() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L, 20L));
        AiReviewRuleEntity v1 = draftRule(19L, 44L, 7L, "published");
        v1.setVersionNumber(1);
        AiReviewRuleEntity v2 = draftRule(20L, 44L, 8L, "published");
        v2.setVersionNumber(2);
        when(aiReviewRuleMapper.selectByTaskIdOrderByVersionAsc(44L)).thenReturn(List.of(v1, v2));
        PromptVersionEntity promptV1 = promptVersion(7L, "Review prompt v1");
        PromptVersionEntity promptV2 = promptVersion(8L, "Review prompt v2");
        when(promptVersionService.findById(7L)).thenReturn(promptV1);
        when(promptVersionService.findById(8L)).thenReturn(promptV2);

        List<AiReviewRuleView> result = service.listRules(44L, 1001L);

        assertThat(result).extracting(view -> view.rule().getVersionNumber()).containsExactly(1, 2);
        assertThat(result).extracting(AiReviewRuleView::isCurrent).containsExactly(false, true);
        assertThat(result).extracting(AiReviewRuleView::promptVersion).containsExactly(promptV1, promptV2);
    }

    @Test
    void listRules_marks_all_rules_non_current_when_task_pointer_is_null() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L, null));
        AiReviewRuleEntity v1 = draftRule(19L, 44L, 7L, "published");
        when(aiReviewRuleMapper.selectByTaskIdOrderByVersionAsc(44L)).thenReturn(List.of(v1));
        when(promptVersionService.findById(7L)).thenReturn(promptVersion(7L, "Review prompt"));

        List<AiReviewRuleView> result = service.listRules(44L, 1001L);

        assertThat(result).extracting(AiReviewRuleView::isCurrent).containsExactly(false);
    }

    @Test
    void listRules_returns_not_found_for_missing_or_cross_owner_task() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 2002L));

        assertThatThrownBy(() -> service.listRules(44L, 1001L))
            .isInstanceOf(TaskNotFoundException.class)
            .hasMessageContaining("Task not found");
    }

    @Test
    void listRules_returns_prompt_version_not_found_when_rule_points_to_missing_prompt() {
        when(taskMapper.selectById(44L)).thenReturn(task(44L, 1001L, 19L));
        AiReviewRuleEntity rule = draftRule(19L, 44L, 7L, "published");
        when(aiReviewRuleMapper.selectByTaskIdOrderByVersionAsc(44L)).thenReturn(List.of(rule));
        when(promptVersionService.findById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.listRules(44L, 1001L))
            .isInstanceOf(PromptVersionNotFoundException.class)
            .hasMessageContaining("Prompt version not found");
    }

    private AiReviewRuleRequest request(Long taskId, String prompt, List<String> dimensions, String threshold) {
        return new AiReviewRuleRequest(taskId, prompt, dimensions, new BigDecimal(threshold));
    }

    private TaskEntity task(Long taskId, Long ownerId) {
        return task(taskId, ownerId, null);
    }

    private TaskEntity task(Long taskId, Long ownerId, Long currentAiReviewRuleId) {
        TaskEntity entity = new TaskEntity();
        entity.setId(taskId);
        entity.setOwnerId(ownerId);
        entity.setCurrentAiReviewRuleId(currentAiReviewRuleId);
        return entity;
    }

    private PromptVersionEntity promptVersion(Long id, String content) {
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setId(id);
        entity.setContent(content);
        entity.setVersionNumber(id.intValue());
        entity.setStatusCode("draft");
        return entity;
    }

    private AiReviewRuleEntity draftRule(Long id, Long taskId, Long promptVersionId, String status) {
        AiReviewRuleEntity entity = new AiReviewRuleEntity();
        entity.setId(id);
        entity.setTaskId(taskId);
        entity.setVersionNumber(3);
        entity.setCurrentPromptVersionId(promptVersionId);
        entity.setDimensionsJson("[\"accuracy\"]");
        entity.setThreshold(new BigDecimal("0.8000"));
        entity.setStatusCode(status);
        entity.setCreatedBy(1001L);
        return entity;
    }

    private AiReviewRuleEntity publishedRule(Long id, Long taskId, Long promptVersionId) {
        AiReviewRuleEntity entity = draftRule(id, taskId, promptVersionId, "published");
        return entity;
    }
}
