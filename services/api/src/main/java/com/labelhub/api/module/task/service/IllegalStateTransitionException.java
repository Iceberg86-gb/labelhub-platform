package com.labelhub.api.module.task.service;

import com.labelhub.api.generated.model.TaskStatus;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(TaskStatus from, TaskStatus to, String reason) {
        super("Illegal task transition from " + from.getValue() + " to " + to.getValue() + ": " + reason);
    }
}
