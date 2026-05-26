package com.labelhub.api.module.dataset.exception;

public class TaskPublishedLockException extends RuntimeException {

    public TaskPublishedLockException(Long taskId) {
        super("Published task cannot change current dataset: taskId=" + taskId);
    }
}
