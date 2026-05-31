package com.labelhub.api.module.dataset.service;

import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import java.util.List;

public record DatasetItemBulkUpdateResult(
    List<DatasetItemEntity> updated,
    List<DatasetItemUpdateSkipped> skippedLocked
) {
}
