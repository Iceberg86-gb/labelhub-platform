package com.labelhub.api.module.dataset.exception;

public class InvalidDatasetForTaskException extends RuntimeException {

    public InvalidDatasetForTaskException(Long datasetId, Long taskId) {
        super("Dataset " + datasetId + " does not belong to task " + taskId);
    }
}
