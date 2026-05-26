package com.labelhub.api.module.session.exception;

public class TaskNotAvailableException extends RuntimeException {
    public TaskNotAvailableException(Long taskId) {
        super("Task is not available for claim: " + taskId);
    }
}
