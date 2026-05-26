package com.labelhub.api.module.quality.exception;

public class LedgerEntryTypeNotSupportedException extends RuntimeException {
    public LedgerEntryTypeNotSupportedException(String entryType) {
        super("Quality ledger entry type is not supported: " + entryType);
    }
}
