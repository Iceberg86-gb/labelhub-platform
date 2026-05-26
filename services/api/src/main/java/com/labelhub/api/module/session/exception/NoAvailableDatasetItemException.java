package com.labelhub.api.module.session.exception;

public class NoAvailableDatasetItemException extends RuntimeException {
    public NoAvailableDatasetItemException(Long taskId, Long datasetId) {
        super("No available dataset item for task " + taskId + " and dataset " + datasetId);
    }
}
