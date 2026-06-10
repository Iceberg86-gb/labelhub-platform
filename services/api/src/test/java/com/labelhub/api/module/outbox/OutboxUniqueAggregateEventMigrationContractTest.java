package com.labelhub.api.module.outbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxUniqueAggregateEventMigrationContractTest {

    @Test
    void outbox_enforces_unique_aggregate_event_after_deduplicating_existing_rows() throws IOException {
        String migrationText = Files.list(Path.of("src/main/resources/db/migration"))
            .sorted(Comparator.comparing(Path::getFileName))
            .filter(path -> path.getFileName().toString().endsWith(".sql"))
            .map(this::read)
            .collect(Collectors.joining("\n"));

        assertThat(migrationText)
            .contains("ADD UNIQUE KEY uk_outbox_aggregate_event (aggregate_type, aggregate_id, event_type)");

        // The dedup DELETE must run before the unique key is added, otherwise the migration would
        // fail on databases that already contain duplicate outbox rows.
        int dedupIndex = migrationText.indexOf("DELETE o FROM outbox o");
        int uniqueIndex = migrationText.indexOf("uk_outbox_aggregate_event");
        assertThat(dedupIndex).isGreaterThanOrEqualTo(0);
        assertThat(uniqueIndex).isGreaterThan(dedupIndex);
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read migration " + path, exception);
        }
    }
}
