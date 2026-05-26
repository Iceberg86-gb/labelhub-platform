package com.labelhub.api.module.ai.service.view;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import com.labelhub.api.module.ai.provider.AiCallResult;
import java.util.List;

public record AiReviewResultView(
    AiCallEntity aiCall,
    AiCallResult providerResult,
    List<AiCallInFieldEntity> fieldRows,
    boolean idempotencyHit
) {}
