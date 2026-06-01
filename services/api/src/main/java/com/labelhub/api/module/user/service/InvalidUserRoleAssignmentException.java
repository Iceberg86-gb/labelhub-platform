package com.labelhub.api.module.user.service;

public class InvalidUserRoleAssignmentException extends RuntimeException {

    public InvalidUserRoleAssignmentException(String message) {
        super(message);
    }
}
