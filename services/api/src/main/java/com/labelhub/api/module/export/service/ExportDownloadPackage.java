package com.labelhub.api.module.export.service;

public record ExportDownloadPackage(String fileName, String contentType, byte[] content) {
}
