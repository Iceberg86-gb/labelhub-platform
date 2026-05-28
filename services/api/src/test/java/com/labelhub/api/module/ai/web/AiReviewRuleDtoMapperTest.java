package com.labelhub.api.module.ai.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.AiReviewRule;
import com.labelhub.api.generated.model.AiReviewRuleStatus;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.service.view.AiReviewRuleView;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewRuleDtoMapperTest {

    private final AiReviewRuleDtoMapper mapper = new AiReviewRuleDtoMapper(new ObjectMapper());

    @Test
    void maps_rule_with_prompt_version_content_for_display() {
        AiReviewRuleEntity rule = new AiReviewRuleEntity();
        rule.setId(19L);
        rule.setTaskId(44L);
        rule.setVersionNumber(3);
        rule.setCurrentPromptVersionId(7L);
        rule.setDimensionsJson("[\"accuracy\",\"safety\"]");
        rule.setThreshold(new BigDecimal("0.8000"));
        rule.setStatusCode("draft");
        rule.setCreatedAt(LocalDateTime.of(2026, 5, 28, 13, 0));

        PromptVersionEntity promptVersion = new PromptVersionEntity();
        promptVersion.setId(7L);
        promptVersion.setContent("Review prompt");

        AiReviewRule dto = mapper.toRule(new AiReviewRuleView(rule, promptVersion));

        assertThat(dto.getId()).isEqualTo(19L);
        assertThat(dto.getTaskId()).isEqualTo(44L);
        assertThat(dto.getVersionNo()).isEqualTo(3);
        assertThat(dto.getPromptVersionId()).isEqualTo(7L);
        assertThat(dto.getPromptTemplate()).isEqualTo("Review prompt");
        assertThat(dto.getDimensions()).containsExactly("accuracy", "safety");
        assertThat(dto.getThreshold()).isEqualByComparingTo("0.8000");
        assertThat(dto.getStatus()).isEqualTo(AiReviewRuleStatus.DRAFT);
        assertThat(dto.getCreatedAt()).isNotNull();
    }
}
