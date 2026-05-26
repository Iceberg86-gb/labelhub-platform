package com.labelhub.api.module.session.exception;

public class DraftNotFoundException extends RuntimeException {

    public DraftNotFoundException(Long sessionId) {
        super("Draft not found for session " + sessionId);
    }
}
