package com.labelhub.api.module.ai.service.view;

import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;

public record AiReviewRuleView(
    AiReviewRuleEntity rule,
    PromptVersionEntity promptVersion,
    boolean isCurrent
) {
}
