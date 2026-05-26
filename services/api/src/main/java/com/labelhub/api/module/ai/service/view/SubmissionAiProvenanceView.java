package com.labelhub.api.module.ai.service.view;

import com.labelhub.api.module.ai.entity.AiCallEntity;
import com.labelhub.api.module.ai.entity.AiCallInFieldEntity;
import java.util.List;

public record SubmissionAiProvenanceView(
    Long submissionId,
    List<AiCallEntity> aiCalls,
    List<AiCallInFieldEntity> fieldRows
) {}
