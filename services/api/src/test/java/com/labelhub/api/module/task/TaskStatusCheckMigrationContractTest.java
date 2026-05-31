package com.labelhub.api.module.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusCheckMigrationContractTest {

    @Test
    void tasks_status_has_database_check_constraint_for_known_statuses() throws IOException {
        String migrationText = Files.list(Path.of("src/main/resources/db/migration"))
            .sorted(Comparator.comparing(Path::getFileName))
            .filter(path -> path.getFileName().toString().endsWith(".sql"))
            .map(this::read)
            .collect(Collectors.joining("\n"));

        assertThat(migrationText)
            .contains("CONSTRAINT chk_tasks_status")
            .contains("status IN ('draft', 'published', 'paused', 'ended')")
            .contains("SELECT COUNT(*) INTO @invalid_task_status_count")
            .contains("WHERE status NOT IN ('draft', 'published', 'paused', 'ended')");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read migration " + path, exception);
        }
    }
}
