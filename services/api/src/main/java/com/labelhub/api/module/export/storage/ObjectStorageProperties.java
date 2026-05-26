package com.labelhub.api.module.export.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "labelhub.object-storage")
public record ObjectStorageProperties(
    String endpoint,
    String region,
    String accessKey,
    String secretKey,
    String bucket,
    boolean pathStyleAccess
) {}
