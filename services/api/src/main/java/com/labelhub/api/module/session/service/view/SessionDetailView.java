package com.labelhub.api.module.session.service.view;

import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import com.labelhub.api.module.session.entity.DraftEntity;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.task.entity.TaskEntity;

public class SessionDetailView {

    private final SessionEntity session;
    private final TaskEntity task;
    private final SchemaVersionEntity schemaVersion;
    private final DatasetItemEntity datasetItem;
    private final DraftEntity latestDraft;

    public SessionDetailView(
        SessionEntity session,
        TaskEntity task,
        SchemaVersionEntity schemaVersion,
        DatasetItemEntity datasetItem,
        DraftEntity latestDraft
    ) {
        this.session = session;
        this.task = task;
        this.schemaVersion = schemaVersion;
        this.datasetItem = datasetItem;
        this.latestDraft = latestDraft;
    }

    public SessionEntity getSession() { return session; }
    public TaskEntity getTask() { return task; }
    public SchemaVersionEntity getSchemaVersion() { return schemaVersion; }
    public DatasetItemEntity getDatasetItem() { return datasetItem; }
    public DraftEntity getLatestDraft() { return latestDraft; }
}
