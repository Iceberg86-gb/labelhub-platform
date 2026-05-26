package com.labelhub.api.module.export.web;

import com.labelhub.api.generated.model.ExportFileEntry;
import com.labelhub.api.generated.model.ExportSnapshot;
import com.labelhub.api.generated.model.ExportSnapshotDiff;
import com.labelhub.api.generated.model.ExportSnapshotDiffFileLevelMatchesInner;
import com.labelhub.api.generated.model.ExportSnapshotDiffHashMatches;
import com.labelhub.api.generated.model.PagedExportSnapshots;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import com.labelhub.api.module.export.service.ExportSnapshotDiffView;
import com.labelhub.api.module.task.service.PagedResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExportDtoMapper {

    public ExportSnapshot toExportSnapshot(ExportSnapshotEntity entity) {
        ExportSnapshot dto = new ExportSnapshot();
        dto.setId(entity.getId());
        dto.setExportJobId(entity.getExportJobId());
        dto.setTaskId(entity.getTaskId());
        dto.setFileHash(entity.getFileHash());
        dto.setManifestHash(entity.getManifestHash());
        dto.setSourceStateHash(entity.getSourceStateHash());
        dto.setObjectKey(entity.getObjectKey());
        dto.setFileManifest(toFileEntries(entity.getFileManifest()));
        dto.setRecordCounts(entity.getRecordCounts());
        dto.setCanonicalizationVersion(entity.getCanonicalizationVersion());
        dto.setGeneratedAt(entity.getGeneratedAt() == null ? null : entity.getGeneratedAt().toString());
        return dto;
    }

    public PagedExportSnapshots toPagedExportSnapshots(PagedResult<ExportSnapshotEntity> result) {
        PagedExportSnapshots dto = new PagedExportSnapshots();
        dto.setItems(result.items().stream().map(this::toExportSnapshot).toList());
        dto.setTotal(Math.toIntExact(result.total()));
        dto.setPage(Math.toIntExact(result.page()));
        dto.setSize(Math.toIntExact(result.size()));
        return dto;
    }

    public ExportSnapshotDiff toExportSnapshotDiff(ExportSnapshotDiffView view) {
        ExportSnapshotDiff dto = new ExportSnapshotDiff();
        dto.setEqual(view.equal());
        dto.setBaseSnapshotId(view.baseSnapshotId());
        dto.setCompareSnapshotId(view.compareSnapshotId());

        ExportSnapshotDiffHashMatches hashMatches = new ExportSnapshotDiffHashMatches();
        hashMatches.setFileHash(view.fileHashMatch());
        hashMatches.setManifestHash(view.manifestHashMatch());
        hashMatches.setSourceStateHash(view.sourceStateHashMatch());
        dto.setHashMatches(hashMatches);

        dto.setFileLevelMatches(view.fileLevelMatches().stream().map(match -> {
            ExportSnapshotDiffFileLevelMatchesInner item = new ExportSnapshotDiffFileLevelMatchesInner();
            item.setFileName(match.fileName());
            item.setBaseSha256(match.baseSha256());
            item.setCompareSha256(match.compareSha256());
            item.setMatch(match.match());
            return item;
        }).toList());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private List<ExportFileEntry> toFileEntries(Map<String, Object> manifest) {
        if (manifest == null || !(manifest.get("files") instanceof List<?> rawFiles)) {
            return List.of();
        }
        List<ExportFileEntry> entries = new ArrayList<>();
        for (Object rawFile : rawFiles) {
            if (rawFile instanceof Map<?, ?> rawMap) {
                entries.add(toFileEntry((Map<String, Object>) rawMap));
            }
        }
        return entries;
    }

    private ExportFileEntry toFileEntry(Map<String, Object> raw) {
        ExportFileEntry entry = new ExportFileEntry();
        entry.setName(String.valueOf(raw.get("name")));
        entry.setSha256(String.valueOf(raw.get("sha256")));
        entry.setLines(toInteger(raw.get("lines")));
        entry.setSizeBytes(toLong(raw.get("sizeBytes")));
        return entry;
    }

    private Integer toInteger(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }
}
