package com.labelhub.api.module.export.web;

import com.labelhub.api.generated.model.CreateTaskExportRequest;
import com.labelhub.api.generated.model.ExportFieldCatalog;
import com.labelhub.api.generated.model.ExportJob;
import com.labelhub.api.generated.model.ExportSnapshot;
import com.labelhub.api.generated.model.ExportSnapshotDiff;
import com.labelhub.api.generated.model.PagedExportSnapshots;
import com.labelhub.api.generated.web.ExportsApi;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.service.ExportFieldMapping;
import com.labelhub.api.module.export.service.ExportFieldMappingColumn;
import com.labelhub.api.module.export.service.ExportDataScope;
import com.labelhub.api.module.export.service.ExportDownloadFile;
import com.labelhub.api.module.export.service.ExportDownloadPackage;
import com.labelhub.api.module.export.service.ExportFieldCatalogService;
import com.labelhub.api.module.export.service.ExportSnapshotPackageType;
import com.labelhub.api.module.export.service.ExportService;
import com.labelhub.api.module.export.service.ExportSnapshotDiffView;
import com.labelhub.api.module.export.service.TrainingExportProfile;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController implements ExportsApi {

    private final ExportService exportService;
    private final ExportFieldCatalogService fieldCatalogService;
    private final ExportDtoMapper dtoMapper;

    public ExportController(ExportService exportService, ExportFieldCatalogService fieldCatalogService, ExportDtoMapper dtoMapper) {
        this.exportService = exportService;
        this.fieldCatalogService = fieldCatalogService;
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
            exportService.requestExport(taskId, currentUserId(), dataScope, toFieldMapping(body), toTrainingProfile(body))
        ));
    }

    @Override
    public ResponseEntity<PagedExportSnapshots> listTaskExports(
        @PathVariable("taskId") Long taskId,
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
        @Valid @RequestParam(value = "archived", required = false, defaultValue = "false") Boolean archived
    ) {
        PagedResult<ExportSnapshotEntity> result = exportService.listSnapshotsForOwner(
            taskId,
            currentUserId(),
            clampMin(page, 1),
            clampSize(size, 20, 100),
            Boolean.TRUE.equals(archived)
        );
        return ResponseEntity.ok(dtoMapper.toPagedExportSnapshots(result));
    }

    @Override
    public ResponseEntity<ExportFieldCatalog> getTaskExportFields(@PathVariable("taskId") Long taskId) {
        return ResponseEntity.ok(dtoMapper.toExportFieldCatalog(
            fieldCatalogService.buildForOwner(taskId, currentUserId())
        ));
    }

    @Override
    public ResponseEntity<ExportSnapshot> getExportSnapshot(@PathVariable("snapshotId") Long snapshotId) {
        ExportSnapshotEntity snapshot = exportService.getSnapshotForOwner(snapshotId, currentUserId());
        return ResponseEntity.ok(dtoMapper.toExportSnapshot(snapshot));
    }

    @Override
    public ResponseEntity<ExportSnapshot> archiveExportSnapshot(@PathVariable("snapshotId") Long snapshotId) {
        ExportSnapshotEntity snapshot = exportService.archiveSnapshotForOwner(snapshotId, currentUserId());
        return ResponseEntity.ok(dtoMapper.toExportSnapshot(snapshot));
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> downloadExportSnapshotFile(
        @PathVariable("snapshotId") Long snapshotId,
        @PathVariable("fileName") String fileName
    ) {
        ExportDownloadFile downloadFile = exportService.downloadSnapshotFile(snapshotId, fileName, currentUserId());
        ByteArrayResource resource = new ByteArrayResource(downloadFile.content());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(downloadFile.contentType()))
            .contentLength(downloadFile.content().length)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(downloadFile.fileName()).build().toString()
            )
            .body(resource);
    }

    @Override
    public ResponseEntity<org.springframework.core.io.Resource> downloadExportSnapshotPackage(
        @PathVariable("snapshotId") Long snapshotId,
        @PathVariable("packageType") com.labelhub.api.generated.model.ExportSnapshotPackageType packageType
    ) {
        ExportDownloadPackage downloadPackage = exportService.downloadSnapshotPackage(
            snapshotId,
            ExportSnapshotPackageType.fromValue(packageType.getValue()),
            currentUserId()
        );
        ByteArrayResource resource = new ByteArrayResource(downloadPackage.content());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(downloadPackage.contentType()))
            .contentLength(downloadPackage.content().length)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(downloadPackage.fileName()).build().toString()
            )
            .body(resource);
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

    private TrainingExportProfile toTrainingProfile(CreateTaskExportRequest body) {
        if (body == null || body.getTrainingFormat() == null) {
            return TrainingExportProfile.flatTable();
        }
        TrainingExportProfile.Format format = TrainingExportProfile.Format.fromValue(body.getTrainingFormat().getValue());
        com.labelhub.api.generated.model.TrainingExportProfile profile = body.getTrainingProfile();
        String promptSource = profile == null ? null : profile.getPromptSource();
        String completionSource = profile == null ? null : profile.getCompletionSource();
        String preferenceSource = profile == null ? null : profile.getPreferenceSource();
        Map<String, String> choiceSources = profile == null || profile.getChoiceSources() == null
            ? Map.of()
            : profile.getChoiceSources();
        return switch (format) {
            case OPENAI_CHAT_SFT_JSONL -> TrainingExportProfile.openAiChatSft(promptSource, completionSource);
            case TRL_SFT_JSONL -> TrainingExportProfile.trlSft(promptSource, completionSource);
            case TRL_DPO_JSONL -> TrainingExportProfile.trlDpo(promptSource, preferenceSource, choiceSources);
            case FLAT_TABLE -> TrainingExportProfile.flatTable();
        };
    }
}
