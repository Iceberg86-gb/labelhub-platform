package com.labelhub.api.module.user.service;

public class CannotDeleteOwnerException extends RuntimeException {

    public CannotDeleteOwnerException(Long userId) {
        super("OWNER account cannot be deleted: " + userId);
    }
}
