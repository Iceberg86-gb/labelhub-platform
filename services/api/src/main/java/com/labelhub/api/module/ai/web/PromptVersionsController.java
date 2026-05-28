package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.PromptVersion;
import com.labelhub.api.generated.web.PromptVersionsApi;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.service.PromptVersionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PromptVersionsController implements PromptVersionsApi {

    private final PromptVersionService promptVersionService;
    private final PromptVersionDtoMapper promptVersionDtoMapper;

    public PromptVersionsController(PromptVersionService promptVersionService, PromptVersionDtoMapper promptVersionDtoMapper) {
        this.promptVersionService = promptVersionService;
        this.promptVersionDtoMapper = promptVersionDtoMapper;
    }

    @Override
    public ResponseEntity<PromptVersion> getDefaultPromptVersion() {
        PromptVersionEntity defaultPromptVersion = promptVersionService.resolveDefault();
        if (defaultPromptVersion == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Default prompt version is not configured");
        }
        return ResponseEntity.ok(promptVersionDtoMapper.toPromptVersion(defaultPromptVersion));
    }
}
