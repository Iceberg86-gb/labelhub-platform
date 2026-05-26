package com.labelhub.api.module.session.exception;

public class SessionNotEditableException extends RuntimeException {

    public SessionNotEditableException(Long sessionId, String status) {
        super("Session " + sessionId + " is not editable in status " + status);
    }
}
