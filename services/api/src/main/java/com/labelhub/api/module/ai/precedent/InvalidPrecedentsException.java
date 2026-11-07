package com.labelhub.api.module.ai.precedent;

/** ADR-021 §3: echoed precedents failed the audit check; the result is not recorded. */
public class InvalidPrecedentsException extends RuntimeException {

    public InvalidPrecedentsException(String message) {
        super(message);
    }
}
