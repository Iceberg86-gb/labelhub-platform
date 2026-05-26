package com.labelhub.api.module.session.exception;

public class SessionAlreadySubmittedException extends RuntimeException {

    public SessionAlreadySubmittedException(Long sessionId, String status) {
        super("Session " + sessionId + " cannot be submitted from status " + status);
    }
}
