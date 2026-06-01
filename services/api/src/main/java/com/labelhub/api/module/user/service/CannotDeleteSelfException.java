package com.labelhub.api.module.user.service;

public class CannotDeleteSelfException extends RuntimeException {

    public CannotDeleteSelfException(Long userId) {
        super("User cannot delete their own account: " + userId);
    }
}
