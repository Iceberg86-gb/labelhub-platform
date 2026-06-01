package com.labelhub.api.module.user.service;

public class UserDeletionConflictException extends RuntimeException {

    public UserDeletionConflictException(Long userId) {
        super("User is not active or does not exist: " + userId);
    }
}
