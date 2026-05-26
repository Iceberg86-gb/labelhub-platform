package com.labelhub.api.module.export.service;

import java.util.List;

public record ExportSnapshotDiffView(
    boolean equal,
    Long baseSnapshotId,
    Long compareSnapshotId,
    boolean fileHashMatch,
    boolean manifestHashMatch,
    boolean sourceStateHashMatch,
    List<FileLevelMatch> fileLevelMatches
) {
    public record FileLevelMatch(
        String fileName,
        String baseSha256,
        String compareSha256,
        boolean match
    ) {
    }
}
