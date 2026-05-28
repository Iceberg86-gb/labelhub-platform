package com.labelhub.api.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.mapper.PromptVersionMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptVersionServiceTest {

    private final PromptVersionMapper promptVersionMapper = mock(PromptVersionMapper.class);
    private final Canonicalizer canonicalizer = new Canonicalizer(new ObjectMapper());
    private final PromptVersionService service = new PromptVersionService(promptVersionMapper, canonicalizer);

    @Test
    void create_reuses_existing_prompt_version_with_same_content_hash() {
        PromptVersionEntity existing = promptVersion(4L, 2, "same content", hash("same content"), "draft");
        when(promptVersionMapper.selectByContentHash(hash("same content"))).thenReturn(existing);

        PromptVersionEntity result = service.create("same content", 1001L);

        assertThat(result).isSameAs(existing);
        verify(promptVersionMapper, never()).insert(any());
    }

    @Test
    void create_inserts_draft_prompt_version_with_next_global_version_and_text_hash() {
        when(promptVersionMapper.selectByContentHash(hash("new prompt"))).thenReturn(null);
        when(promptVersionMapper.selectMaxVersionNumber()).thenReturn(2);
        when(promptVersionMapper.insert(any())).thenAnswer(invocation -> {
            PromptVersionEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        });

        PromptVersionEntity result = service.create("new prompt", 1001L);

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getVersionNumber()).isEqualTo(3);
        assertThat(result.getContent()).isEqualTo("new prompt");
        assertThat(result.getContentHash()).isEqualTo(hash("new prompt"));
        assertThat(result.getStatusCode()).isEqualTo("draft");
        assertThat(result.getOwnerId()).isEqualTo(1001L);
    }

    @Test
    void create_returns_existing_prompt_version_when_content_hash_races() {
        PromptVersionEntity existing = promptVersion(8L, 4, "race prompt", hash("race prompt"), "draft");
        when(promptVersionMapper.selectByContentHash(hash("race prompt"))).thenReturn(null, existing);
        when(promptVersionMapper.selectMaxVersionNumber()).thenReturn(3);
        when(promptVersionMapper.insert(any())).thenThrow(new DuplicateKeyException("uk_prompt_versions_hash"));

        PromptVersionEntity result = service.create("race prompt", 1001L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void create_retries_version_number_collision_before_insert_succeeds() {
        when(promptVersionMapper.selectByContentHash(hash("version race"))).thenReturn(null);
        when(promptVersionMapper.selectMaxVersionNumber()).thenReturn(4, 5);
        when(promptVersionMapper.insert(any()))
            .thenThrow(new DuplicateKeyException("uk_prompt_versions_no"))
            .thenAnswer(invocation -> {
                PromptVersionEntity entity = invocation.getArgument(0);
                entity.setId(11L);
                return 1;
            });

        PromptVersionEntity result = service.create("version race", 1001L);

        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getVersionNumber()).isEqualTo(6);
    }

    @Test
    void resolveDefault_returns_latest_published_prompt_version() {
        PromptVersionEntity published = promptVersion(1L, 1, "m3-owner-review-v1", hash("m3-owner-review-v1"), "published");
        when(promptVersionMapper.selectLatestPublished()).thenReturn(published);

        assertThat(service.resolveDefault()).isSameAs(published);
    }

    private String hash(String content) {
        return canonicalizer.sha256Hex(content);
    }

    private PromptVersionEntity promptVersion(Long id, Integer versionNumber, String content, String contentHash, String status) {
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setId(id);
        entity.setVersionNumber(versionNumber);
        entity.setContent(content);
        entity.setContentHash(contentHash);
        entity.setStatusCode(status);
        return entity;
    }
}
