package com.labelhub.api.module.task.service;

public class TaskAccessDeniedException extends RuntimeException {

    public TaskAccessDeniedException(Long taskId, Long ownerId) {
        super("Task " + taskId + " is not owned by user " + ownerId);
    }
}
