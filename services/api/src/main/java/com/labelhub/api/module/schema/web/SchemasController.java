package com.labelhub.api.module.schema.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labelhub.api.generated.model.CreateSchemaRequest;
import com.labelhub.api.generated.model.LabelSchema;
import com.labelhub.api.generated.model.PagedSchemas;
import com.labelhub.api.generated.model.SchemaVersion;
import com.labelhub.api.generated.model.SchemaVersionRequest;
import com.labelhub.api.generated.web.SchemasApi;
import com.labelhub.api.module.schema.entity.LabelSchemaEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.schema.service.SchemaService;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schemas")
public class SchemasController implements SchemasApi {

    private final SchemaService schemaService;
    private final SchemaDtoMapper schemaDtoMapper;

    public SchemasController(SchemaService schemaService, SchemaDtoMapper schemaDtoMapper) {
        this.schemaService = schemaService;
        this.schemaDtoMapper = schemaDtoMapper;
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<LabelSchema> createSchema(@Valid @RequestBody CreateSchemaRequest createSchemaRequest) {
        LabelSchemaEntity created = schemaService.create(
            createSchemaRequest.getTaskId(),
            createSchemaRequest.getName(),
            createSchemaRequest.getDescription(),
            currentUserId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaDtoMapper.toLabelSchema(created));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(produces = "application/json")
    public ResponseEntity<PagedSchemas> listSchemas(
        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
        @RequestParam(value = "q", required = false) String q
    ) {
        Page<LabelSchemaEntity> result = schemaService.list(currentUserId(), page, size, q);
        return ResponseEntity.ok(schemaDtoMapper.toPagedSchemas(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize()));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{schemaId}", produces = "application/json")
    public ResponseEntity<LabelSchema> getSchema(@PathVariable("schemaId") Long schemaId) {
        return ResponseEntity.ok(schemaDtoMapper.toLabelSchema(schemaService.getById(schemaId, currentUserId())));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping(path = "/{schemaId}/versions", consumes = "application/json", produces = "application/json")
    public ResponseEntity<SchemaVersion> publishSchemaVersion(
        @PathVariable("schemaId") Long schemaId,
        @Valid @RequestBody SchemaVersionRequest schemaVersionRequest
    ) {
        SchemaVersionEntity version = schemaService.publishVersion(schemaId, schemaVersionRequest.getSchemaJson(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(schemaDtoMapper.toSchemaVersion(version));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{schemaId}/versions", produces = "application/json")
    public ResponseEntity<List<SchemaVersion>> listSchemaVersions(@PathVariable("schemaId") Long schemaId) {
        return ResponseEntity.ok(schemaService.listVersions(schemaId, currentUserId()).stream().map(schemaDtoMapper::toSchemaVersion).toList());
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{schemaId}/versions/{versionId}", produces = "application/json")
    public ResponseEntity<SchemaVersion> getSchemaVersion(
        @PathVariable("schemaId") Long schemaId,
        @PathVariable("versionId") Long versionId
    ) {
        return ResponseEntity.ok(schemaDtoMapper.toSchemaVersion(schemaService.getVersion(schemaId, versionId, currentUserId())));
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }
}
