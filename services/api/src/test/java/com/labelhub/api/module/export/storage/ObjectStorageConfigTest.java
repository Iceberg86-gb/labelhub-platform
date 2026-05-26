package com.labelhub.api.module.export.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStorageConfigTest {

    @Test
    void s3Client_fails_fast_when_access_key_missing() {
        ObjectStorageConfig config = new ObjectStorageConfig();
        ObjectStorageProperties properties = new ObjectStorageProperties(
            "http://localhost:9000",
            "us-east-1",
            "",
            "secret",
            "labelhub-exports",
            true
        );

        assertThatThrownBy(() -> config.s3Client(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("labelhub.object-storage.access-key");
    }

    @Test
    void s3Client_fails_fast_when_secret_key_missing() {
        ObjectStorageConfig config = new ObjectStorageConfig();
        ObjectStorageProperties properties = new ObjectStorageProperties(
            "http://localhost:9000",
            "us-east-1",
            "access",
            " ",
            "labelhub-exports",
            true
        );

        assertThatThrownBy(() -> config.s3Client(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("labelhub.object-storage.secret-key");
    }
}
