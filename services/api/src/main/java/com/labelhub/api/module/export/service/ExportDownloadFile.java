package com.labelhub.api.module.export.service;

public record ExportDownloadFile(String fileName, String contentType, byte[] content) {
}
