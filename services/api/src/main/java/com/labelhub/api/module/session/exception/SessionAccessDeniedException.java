package com.labelhub.api.module.session.exception;

public class SessionAccessDeniedException extends RuntimeException {
    public SessionAccessDeniedException(Long sessionId, Long labelerId) {
        super("Labeler " + labelerId + " has no access to session " + sessionId);
    }
}
