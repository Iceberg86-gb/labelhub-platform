package com.labelhub.api.module.export.storage;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfig {

    @Bean
    public S3Client s3Client(ObjectStorageProperties properties) {
        requireText(properties.endpoint(), "endpoint");
        requireText(properties.accessKey(), "access-key");
        requireText(properties.secretKey(), "secret-key");
        requireText(properties.bucket(), "bucket");

        String region = hasText(properties.region()) ? properties.region() : "us-east-1";
        return S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint()))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build())
            .build();
    }

    private static void requireText(String value, String property) {
        if (!hasText(value)) {
            throw new IllegalStateException("labelhub.object-storage." + property + " must be configured");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
