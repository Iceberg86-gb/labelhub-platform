package com.labelhub.api.module.session.service;

public record SessionAttachmentDownload(String fileName, String contentType, byte[] content) {
}
