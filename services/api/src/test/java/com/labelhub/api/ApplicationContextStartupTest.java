package com.labelhub.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "labelhub.security.jwt-secret=dev-only-32-bytes-minimum-secret-please-change-me",
    "labelhub.security.internal-token=dev-internal-token",
    "labelhub.object-storage.endpoint=http://localhost:9000",
    "labelhub.object-storage.region=us-east-1",
    "labelhub.object-storage.access-key=test-access-key",
    "labelhub.object-storage.secret-key=test-secret-key",
    "labelhub.object-storage.bucket=labelhub-exports",
    "labelhub.object-storage.path-style-access=true"
})
class ApplicationContextStartupTest {

    @Test
    void context_loads() {
        // Empty body: loading the Spring context is the assertion.
    }
}
