package com.labelhub.api.module.export.web;

import com.labelhub.api.generated.model.ExportSnapshot;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.service.ExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalExportController {

    private final ExportService exportService;
    private final ExportDtoMapper dtoMapper;

    public InternalExportController(ExportService exportService, ExportDtoMapper dtoMapper) {
        this.exportService = exportService;
        this.dtoMapper = dtoMapper;
    }

    @PostMapping("/internal/exports/jobs/{exportJobId}/run")
    public ResponseEntity<ExportSnapshot> runExportJob(@PathVariable("exportJobId") Long exportJobId) {
        ExportSnapshotEntity snapshot = exportService.processExportJob(exportJobId);
        return ResponseEntity.ok(dtoMapper.toExportSnapshot(snapshot));
    }
}
