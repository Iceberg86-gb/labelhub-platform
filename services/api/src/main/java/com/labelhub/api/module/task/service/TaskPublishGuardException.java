package com.labelhub.api.module.task.service;

public class TaskPublishGuardException extends RuntimeException {

    private final String guardName;

    public TaskPublishGuardException(String guardName) {
        super("Task publish guard failed: " + guardName);
        this.guardName = guardName;
    }

    public String getGuardName() {
        return guardName;
    }
}
