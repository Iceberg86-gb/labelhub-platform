package com.labelhub.api.module.task.service;

import com.labelhub.api.generated.model.TaskStatus;

public class TaskEditingLockedException extends RuntimeException {

    public TaskEditingLockedException(TaskStatus status) {
        super("Task basic fields cannot be edited while task is " + status.getValue());
    }
}
