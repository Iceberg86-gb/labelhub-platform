package com.labelhub.api.module.export.storage;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
        putObject(objectKey, content, contentTypeFor(objectKey));
    }

    public void putObject(String objectKey, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .contentType(contentType == null || contentType.isBlank() ? contentTypeFor(objectKey) : contentType)
            .build();
        s3Client.putObject(request, RequestBody.fromBytes(content));
    }

    public void deleteObject(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .build();
        s3Client.deleteObject(request);
    }

    public byte[] getObject(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(objectKey)
            .build();
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        return response.asByteArray();
    }

    public String contentTypeFor(String objectKey) {
        if (objectKey.endsWith(".json") || objectKey.endsWith(".jsonl")) {
            return "application/json";
        }
        if (objectKey.endsWith(".csv")) {
            return "text/csv; charset=utf-8";
        }
        if (objectKey.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "application/octet-stream";
    }
}
