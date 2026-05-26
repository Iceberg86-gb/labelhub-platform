package com.labelhub.api.module.schema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionLifecycleMigrationContractTest {

    @Test
    void migrations_normalize_submission_status_default_to_submitted() throws IOException {
        String migrationText = Files.list(Path.of("src/main/resources/db/migration"))
            .sorted(Comparator.comparing(Path::getFileName))
            .filter(path -> path.getFileName().toString().endsWith(".sql"))
            .map(this::read)
            .collect(Collectors.joining("\n"));

        assertThat(migrationText)
            .contains("DEFAULT 'submitted'")
            .contains("WHERE status = 'under_ai_review'");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read migration " + path, exception);
        }
    }
}
