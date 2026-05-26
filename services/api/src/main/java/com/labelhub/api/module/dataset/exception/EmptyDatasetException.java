package com.labelhub.api.module.dataset.exception;

public class EmptyDatasetException extends RuntimeException {

    public EmptyDatasetException() {
        super("Dataset file must contain at least one item");
    }
}
