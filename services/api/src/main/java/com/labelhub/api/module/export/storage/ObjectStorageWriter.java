package com.labelhub.api.module.export.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class ObjectStorageWriter {

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;

    public ObjectStorageWriter(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    public void putObject(String objectKey, byte[] content) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(contentTypeFor(objectKey))
            .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    private String contentTypeFor(String objectKey) {
        return objectKey.endsWith(".json") || objectKey.endsWith(".jsonl")
            ? "application/json"
            : "application/octet-stream";
    }
}
