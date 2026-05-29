package com.labelhub.api.module.ai.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.AiReviewRule;
import com.labelhub.api.generated.model.AiReviewRuleStatus;
import com.labelhub.api.module.ai.service.view.AiReviewRuleView;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiReviewRuleDtoMapper {

    private static final TypeReference<List<String>> DIMENSIONS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public AiReviewRuleDtoMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiReviewRule toRule(AiReviewRuleView view) {
        AiReviewRule dto = new AiReviewRule();
        dto.setId(view.rule().getId());
        dto.setTaskId(view.rule().getTaskId());
        dto.setVersionNo(view.rule().getVersionNumber());
        dto.setPromptVersionId(view.rule().getCurrentPromptVersionId());
        dto.setPromptTemplate(view.promptVersion().getContent());
        dto.setDimensions(readDimensions(view.rule().getDimensionsJson()));
        dto.setThreshold(view.rule().getThreshold());
        dto.setStatus(AiReviewRuleStatus.fromValue(view.rule().getStatusCode()));
        dto.setIsCurrent(view.isCurrent());
        dto.setCreatedAt(offset(view.rule().getCreatedAt()));
        dto.setActivatedAt(offset(view.rule().getActivatedAt()));
        return dto;
    }

    private List<String> readDimensions(String dimensionsJson) {
        try {
            return objectMapper.readValue(dimensionsJson, DIMENSIONS_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse AI review rule dimensions", exception);
        }
    }

    private java.time.OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
