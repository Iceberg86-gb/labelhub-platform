package com.labelhub.api.module.quality.exception;

public class LedgerEntryPayloadInvalidException extends RuntimeException {
    public LedgerEntryPayloadInvalidException(String message) {
        super(message);
    }
}
