package com.labelhub.api.module.ai.providerconfig;

import com.labelhub.api.generated.model.LlmProviderConfig;
import com.labelhub.api.generated.model.LlmProviderConfigRequest;
import com.labelhub.api.generated.model.LlmProviderTestConnectionRequest;
import com.labelhub.api.generated.model.LlmProviderTestConnectionResponse;
import com.labelhub.api.generated.web.LlmProvidersApi;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/llm/providers")
public class LlmProviderConfigsController implements LlmProvidersApi {

    private final LlmProviderConfigService service;
    private final LlmProviderConfigDtoMapper dtoMapper;

    public LlmProviderConfigsController(
        LlmProviderConfigService service,
        LlmProviderConfigDtoMapper dtoMapper
    ) {
        this.service = service;
        this.dtoMapper = dtoMapper;
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(produces = "application/json")
    public ResponseEntity<List<LlmProviderConfig>> listLlmProviders() {
        return ResponseEntity.ok(service.list(currentUserId()).stream().map(dtoMapper::toDto).toList());
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<LlmProviderConfig> createLlmProvider(@Valid @RequestBody LlmProviderConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toDto(service.create(currentUserId(), createCommand(request))));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{providerConfigId}", produces = "application/json")
    public ResponseEntity<LlmProviderConfig> getLlmProvider(@PathVariable("providerConfigId") Long providerConfigId) {
        return ResponseEntity.ok(dtoMapper.toDto(service.get(currentUserId(), providerConfigId)));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping(path = "/{providerConfigId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LlmProviderConfig> updateLlmProvider(
        @PathVariable("providerConfigId") Long providerConfigId,
        @Valid @RequestBody LlmProviderConfigRequest request
    ) {
        return ResponseEntity.ok(dtoMapper.toDto(service.update(currentUserId(), providerConfigId, updateCommand(request))));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping(path = "/{providerConfigId}")
    public ResponseEntity<Void> deleteLlmProvider(@PathVariable("providerConfigId") Long providerConfigId) {
        service.delete(currentUserId(), providerConfigId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping(path = "/{providerConfigId}:test-connection", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LlmProviderTestConnectionResponse> testLlmProvider(
        @PathVariable("providerConfigId") Long providerConfigId,
        @Valid @RequestBody LlmProviderTestConnectionRequest request
    ) {
        return ResponseEntity.ok(dtoMapper.toDto(service.testSaved(currentUserId(), providerConfigId, testCommand(request))));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping(path = ":test-connection", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LlmProviderTestConnectionResponse> testUnsavedLlmProvider(
        @Valid @RequestBody LlmProviderTestConnectionRequest request
    ) {
        return ResponseEntity.ok(dtoMapper.toDto(service.testUnsaved(currentUserId(), testCommand(request))));
    }

    private LlmProviderConfigCreateCommand createCommand(LlmProviderConfigRequest request) {
        return new LlmProviderConfigCreateCommand(
            request.getProviderType(),
            request.getProviderName(),
            request.getBaseUrl(),
            request.getModelName(),
            request.getSecret(),
            request.getSecretRef(),
            request.getEnabled()
        );
    }

    private LlmProviderConfigUpdateCommand updateCommand(LlmProviderConfigRequest request) {
        return new LlmProviderConfigUpdateCommand(
            request.getProviderType(),
            request.getProviderName(),
            request.getBaseUrl(),
            request.getModelName(),
            request.getSecret(),
            request.getSecretRef(),
            request.getEnabled()
        );
    }

    private LlmProviderConnectionTestCommand testCommand(LlmProviderTestConnectionRequest request) {
        Integer timeoutSeconds = request.getTimeoutSeconds();
        return new LlmProviderConnectionTestCommand(
            request.getProviderType(),
            request.getProviderName(),
            request.getBaseUrl(),
            request.getModelName(),
            request.getSecret(),
            Duration.ofSeconds(timeoutSeconds == null ? 10 : timeoutSeconds)
        );
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }
}
