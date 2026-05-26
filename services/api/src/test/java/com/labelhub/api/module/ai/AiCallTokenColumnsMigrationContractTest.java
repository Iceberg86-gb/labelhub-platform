package com.labelhub.api.module.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallTokenColumnsMigrationContractTest {

    @Test
    void ai_calls_has_four_nullable_token_columns_with_no_default() throws IOException {
        String migrationText = Files.list(Path.of("src/main/resources/db/migration"))
            .sorted(Comparator.comparing(Path::getFileName))
            .filter(path -> path.getFileName().toString().endsWith(".sql"))
            .map(this::read)
            .collect(Collectors.joining("\n"));

        assertThat(migrationText)
            .contains("ADD COLUMN prompt_tokens INT NULL")
            .contains("ADD COLUMN completion_tokens INT NULL")
            .contains("ADD COLUMN total_tokens INT NULL")
            .contains("ADD COLUMN cache_hit_tokens INT NULL")
            .doesNotContain("prompt_tokens INT NOT NULL")
            .doesNotContain("completion_tokens INT NOT NULL")
            .doesNotContain("total_tokens INT NOT NULL")
            .doesNotContain("cache_hit_tokens INT NOT NULL")
            .doesNotContain("prompt_tokens INT NULL DEFAULT")
            .doesNotContain("completion_tokens INT NULL DEFAULT")
            .doesNotContain("total_tokens INT NULL DEFAULT")
            .doesNotContain("cache_hit_tokens INT NULL DEFAULT");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read migration " + path, exception);
        }
    }
}
