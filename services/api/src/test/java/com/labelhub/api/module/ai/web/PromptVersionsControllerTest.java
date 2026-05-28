package com.labelhub.api.module.ai.web;

import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.service.PromptVersionService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptVersionsControllerTest {

    private final PromptVersionService promptVersionService = mock(PromptVersionService.class);
    private final PromptVersionDtoMapper promptVersionDtoMapper = new PromptVersionDtoMapper();
    private final PromptVersionsController controller = new PromptVersionsController(promptVersionService, promptVersionDtoMapper);

    @Test
    void getDefaultPromptVersion_returns_latest_published_prompt_version() {
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setId(1L);
        entity.setVersionNumber(1);
        entity.setContent("m3-owner-review-v1");
        entity.setContentHash("fa76977fd0bdc3f0cc7336855006669f2950381f1a0dc4f0803458bb6f06d456");
        entity.setStatusCode("published");
        entity.setPublishedAt(LocalDateTime.parse("2026-05-28T12:00:00"));
        entity.setCreatedAt(LocalDateTime.parse("2026-05-28T12:00:00"));
        when(promptVersionService.resolveDefault()).thenReturn(entity);

        var response = controller.getDefaultPromptVersion();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(1L);
        assertThat(response.getBody().getVersionNo()).isEqualTo(1);
        assertThat(response.getBody().getContent()).isEqualTo("m3-owner-review-v1");
        assertThat(response.getBody().getStatus().getValue()).isEqualTo("published");
    }

    @Test
    void getDefaultPromptVersion_returns_404_when_no_published_prompt_exists() {
        when(promptVersionService.resolveDefault()).thenReturn(null);

        assertThatThrownBy(controller::getDefaultPromptVersion)
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Default prompt version is not configured");
    }
}
