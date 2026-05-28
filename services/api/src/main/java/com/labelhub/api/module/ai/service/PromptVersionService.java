package com.labelhub.api.module.ai.service;

import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.mapper.PromptVersionMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromptVersionService {

    private static final int MAX_INSERT_ATTEMPTS = 3;

    private final PromptVersionMapper promptVersionMapper;
    private final Canonicalizer canonicalizer;

    public PromptVersionService(PromptVersionMapper promptVersionMapper, Canonicalizer canonicalizer) {
        this.promptVersionMapper = promptVersionMapper;
        this.canonicalizer = canonicalizer;
    }

    @Transactional
    public PromptVersionEntity create(String content, Long ownerId) {
        Objects.requireNonNull(content, "content must not be null");
        String contentHash = canonicalizer.sha256Hex(content);
        PromptVersionEntity existing = promptVersionMapper.selectByContentHash(contentHash);
        if (existing != null) {
            return existing;
        }

        for (int attempt = 1; attempt <= MAX_INSERT_ATTEMPTS; attempt++) {
            PromptVersionEntity entity = draftEntity(content, ownerId, contentHash, nextVersionNumber());
            try {
                promptVersionMapper.insert(entity);
                return entity;
            } catch (DuplicateKeyException exception) {
                PromptVersionEntity byHash = promptVersionMapper.selectByContentHash(contentHash);
                if (byHash != null) {
                    return byHash;
                }
                if (attempt == MAX_INSERT_ATTEMPTS) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Prompt version insert retry loop exhausted unexpectedly");
    }

    public PromptVersionEntity findById(Long id) {
        return promptVersionMapper.selectById(id);
    }

    public PromptVersionEntity findByContentHash(String contentHash) {
        return promptVersionMapper.selectByContentHash(contentHash);
    }

    public PromptVersionEntity resolveDefault() {
        return promptVersionMapper.selectLatestPublished();
    }

    private PromptVersionEntity draftEntity(String content, Long ownerId, String contentHash, int versionNumber) {
        PromptVersionEntity entity = new PromptVersionEntity();
        entity.setVersionNumber(versionNumber);
        entity.setContent(content);
        entity.setContentHash(contentHash);
        entity.setStatusCode("draft");
        entity.setOwnerId(ownerId);
        return entity;
    }

    private int nextVersionNumber() {
        Integer maxVersionNumber = promptVersionMapper.selectMaxVersionNumber();
        return maxVersionNumber == null ? 1 : maxVersionNumber + 1;
    }
}
