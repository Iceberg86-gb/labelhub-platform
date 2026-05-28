package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.PromptVersion;
import com.labelhub.api.generated.model.PromptVersionStatus;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class PromptVersionDtoMapper {

    public PromptVersion toPromptVersion(PromptVersionEntity entity) {
        PromptVersion dto = new PromptVersion();
        dto.setId(entity.getId());
        dto.setVersionNo(entity.getVersionNumber());
        dto.setContent(entity.getContent());
        dto.setContentHash(entity.getContentHash());
        dto.setStatus(PromptVersionStatus.fromValue(entity.getStatusCode()));
        dto.setOwnerId(entity.getOwnerId());
        dto.setPublishedAt(offset(entity.getPublishedAt()));
        dto.setCreatedAt(offset(entity.getCreatedAt()));
        return dto;
    }

    private java.time.OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
