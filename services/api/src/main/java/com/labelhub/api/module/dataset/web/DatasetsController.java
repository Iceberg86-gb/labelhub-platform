package com.labelhub.api.module.dataset.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labelhub.api.generated.model.Dataset;
import com.labelhub.api.generated.model.DatasetItem;
import com.labelhub.api.generated.model.DatasetItemBulkUpdateItem;
import com.labelhub.api.generated.model.DatasetItemBulkUpdateRequest;
import com.labelhub.api.generated.model.DatasetItemBulkUpdateResult;
import com.labelhub.api.generated.model.DatasetItemBulkUpdateSkipped;
import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.generated.model.PagedDatasets;
import com.labelhub.api.generated.model.PagedDatasetItems;
import com.labelhub.api.generated.web.DatasetsApi;
import com.labelhub.api.module.dataset.entity.DatasetEntity;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import com.labelhub.api.module.dataset.service.DatasetImportService;
import com.labelhub.api.module.dataset.service.DatasetItemService;
import com.labelhub.api.module.dataset.service.DatasetItemUpdateCommand;
import com.labelhub.api.security.JwtPrincipal;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/datasets")
public class DatasetsController implements DatasetsApi {

    private final DatasetImportService datasetImportService;
    private final DatasetItemService datasetItemService;

    public DatasetsController(DatasetImportService datasetImportService, DatasetItemService datasetItemService) {
        this.datasetImportService = datasetImportService;
        this.datasetItemService = datasetItemService;
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping(consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<Dataset> uploadDataset(
        @RequestPart(value = "file", required = true) MultipartFile file,
        @Valid @RequestParam(value = "taskId", required = true) Long taskId,
        @Valid @RequestParam(value = "sourceName", required = false) String sourceName,
        @Valid @RequestParam(value = "format", required = false) DatasetImportFormat format
    ) {
        String resolvedSourceName = resolvedSourceName(sourceName, file);
        try {
            DatasetEntity dataset = datasetImportService.importDataset(
                currentUserId(),
                taskId,
                resolvedSourceName,
                format,
                file.getInputStream()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(dataset));
        } catch (IOException exception) {
            throw new InvalidDatasetFileException("Unable to read uploaded dataset file");
        }
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(produces = "application/json")
    public ResponseEntity<PagedDatasets> listDatasets(
        @NotNull @Valid @RequestParam(value = "taskId", required = true) Long taskId,
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        Page<DatasetEntity> result = datasetImportService.listByTask(currentUserId(), taskId, page, size);
        PagedDatasets response = new PagedDatasets();
        response.setItems(result.getRecords().stream().map(this::toDto).toList());
        response.setTotal(result.getTotal());
        response.setPage((int) result.getCurrent());
        response.setSize((int) result.getSize());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{datasetId}", produces = "application/json")
    public ResponseEntity<Dataset> getDataset(@PathVariable("datasetId") Long datasetId) {
        return ResponseEntity.ok(toDto(datasetImportService.getDataset(datasetId, currentUserId())));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{datasetId}/items", produces = "application/json")
    public ResponseEntity<PagedDatasetItems> listDatasetItems(
        @PathVariable("datasetId") Long datasetId,
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        IPage<DatasetItemEntity> result = datasetItemService.listItems(currentUserId(), datasetId, page, size);
        PagedDatasetItems response = new PagedDatasetItems();
        response.setItems(result.getRecords().stream().map(this::toItemDto).toList());
        response.setTotal(result.getTotal());
        response.setPage((int) result.getCurrent());
        response.setSize((int) result.getSize());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping(path = "/{datasetId}/items:bulk-update", consumes = "application/json", produces = "application/json")
    public ResponseEntity<DatasetItemBulkUpdateResult> bulkUpdateDatasetItems(
        @PathVariable("datasetId") Long datasetId,
        @Valid @RequestBody DatasetItemBulkUpdateRequest request
    ) {
        List<DatasetItemUpdateCommand> commands = request == null || request.getItems() == null
            ? List.of()
            : request.getItems().stream().map(this::toCommand).toList();
        com.labelhub.api.module.dataset.service.DatasetItemBulkUpdateResult outcome =
            datasetItemService.bulkUpdate(currentUserId(), datasetId, commands);
        DatasetItemBulkUpdateResult response = new DatasetItemBulkUpdateResult();
        response.setUpdated(outcome.updated().stream().map(this::toItemDto).toList());
        response.setSkippedLocked(outcome.skippedLocked().stream().map(this::toSkippedDto).toList());
        return ResponseEntity.ok(response);
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }

    private String resolvedSourceName(String sourceName, MultipartFile file) {
        if (sourceName != null && !sourceName.isBlank()) {
            return sourceName;
        }
        return file.getOriginalFilename();
    }

    private Dataset toDto(DatasetEntity entity) {
        Dataset dto = new Dataset();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setSourceType(entity.getSourceType());
        dto.setSourceName(entity.getSourceName());
        dto.setOriginalFileKey(entity.getOriginalFileKey());
        dto.setItemCount(entity.getItemCount());
        dto.setImportStatus(entity.getImportStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    private DatasetItemUpdateCommand toCommand(DatasetItemBulkUpdateItem item) {
        return new DatasetItemUpdateCommand(item.getId(), item.getItemPayload());
    }

    private DatasetItem toItemDto(DatasetItemEntity entity) {
        DatasetItem dto = new DatasetItem();
        dto.setId(entity.getId());
        dto.setDatasetId(entity.getDatasetId());
        dto.setTaskId(entity.getTaskId());
        dto.setOrdinal(entity.getOrdinal());
        dto.setItemPayload(entity.getItemPayload());
        dto.setItemHash(entity.getItemHash());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    private DatasetItemBulkUpdateSkipped toSkippedDto(
        com.labelhub.api.module.dataset.service.DatasetItemUpdateSkipped skipped
    ) {
        DatasetItemBulkUpdateSkipped dto = new DatasetItemBulkUpdateSkipped();
        dto.setId(skipped.id());
        dto.setReason(skipped.reason());
        return dto;
    }
}
