package com.labelhub.api.module.export.web;

import com.labelhub.api.generated.model.CreateTaskExportRequest;
import com.labelhub.api.generated.model.ExportJob;
import com.labelhub.api.generated.model.ExportSnapshot;
import com.labelhub.api.generated.model.ExportSnapshotDiff;
import com.labelhub.api.generated.model.PagedExportSnapshots;
import com.labelhub.api.generated.web.ExportsApi;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.service.ExportFieldMapping;
import com.labelhub.api.module.export.service.ExportFieldMappingColumn;
import com.labelhub.api.module.export.service.ExportDataScope;
import com.labelhub.api.module.export.service.ExportService;
import com.labelhub.api.module.export.service.ExportSnapshotDiffView;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController implements ExportsApi {

    private final ExportService exportService;
    private final ExportDtoMapper dtoMapper;

    public ExportController(ExportService exportService, ExportDtoMapper dtoMapper) {
        this.exportService = exportService;
        this.dtoMapper = dtoMapper;
    }

    @Override
    public ResponseEntity<ExportJob> createTaskExport(
        @PathVariable("taskId") Long taskId,
        @Valid @RequestBody(required = false) CreateTaskExportRequest body
    ) {
        ExportDataScope dataScope = ExportDataScope.fromMode(
            body == null || body.getMode() == null ? null : body.getMode().getValue()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toExportJob(
            exportService.requestExport(taskId, currentUserId(), dataScope, toFieldMapping(body))
        ));
    }

    @Override
    public ResponseEntity<PagedExportSnapshots> listTaskExports(
        @PathVariable("taskId") Long taskId,
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        PagedResult<ExportSnapshotEntity> result = exportService.listSnapshotsForOwner(
            taskId,
            currentUserId(),
            clampMin(page, 1),
            clampSize(size, 20, 100)
        );
        return ResponseEntity.ok(dtoMapper.toPagedExportSnapshots(result));
    }

    @Override
    public ResponseEntity<ExportSnapshot> getExportSnapshot(@PathVariable("snapshotId") Long snapshotId) {
        ExportSnapshotEntity snapshot = exportService.getSnapshotForOwner(snapshotId, currentUserId());
        return ResponseEntity.ok(dtoMapper.toExportSnapshot(snapshot));
    }

    @Override
    public ResponseEntity<ExportSnapshotDiff> diffExportSnapshots(
        @PathVariable("snapshotId") Long snapshotId,
        @NotNull @Valid @RequestParam(value = "compareWith", required = true) Long compareWith
    ) {
        ExportSnapshotDiffView diff = exportService.diffSnapshotsForOwner(snapshotId, compareWith, currentUserId());
        return ResponseEntity.ok(dtoMapper.toExportSnapshotDiff(diff));
    }

    private long clampMin(Integer value, long minimum) {
        if (value == null || value < minimum) {
            return minimum;
        }
        return value;
    }

    private long clampSize(Integer value, long defaultValue, long maximum) {
        long effective = value == null ? defaultValue : value;
        if (effective < 1) {
            return defaultValue;
        }
        return Math.min(effective, maximum);
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }

    private ExportFieldMapping toFieldMapping(CreateTaskExportRequest body) {
        if (body == null || body.getFieldMapping() == null || body.getFieldMapping().getColumns() == null) {
            return ExportFieldMapping.empty();
        }
        List<ExportFieldMappingColumn> columns = body.getFieldMapping().getColumns().stream()
            .map(column -> new ExportFieldMappingColumn(
                column.getSource(),
                column.getColumnName(),
                column.getIncluded() == null || column.getIncluded()
            ))
            .toList();
        return new ExportFieldMapping(columns);
    }
}
