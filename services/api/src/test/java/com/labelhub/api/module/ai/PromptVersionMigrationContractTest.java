package com.labelhub.api.module.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptVersionMigrationContractTest {

    @Test
    void prompt_versions_migration_uses_global_immutable_version_shape() throws IOException {
        String migrationText = migrationsText();

        assertThat(migrationText)
            .contains("CREATE TABLE prompt_versions")
            .contains("version_no INT NOT NULL")
            .contains("content TEXT NOT NULL")
            .contains("content_hash CHAR(64) NOT NULL")
            .contains("status VARCHAR(32) NOT NULL DEFAULT 'draft'")
            .contains("owner_id BIGINT")
            .contains("UNIQUE KEY uk_prompt_versions_no (version_no)")
            .contains("UNIQUE KEY uk_prompt_versions_hash (content_hash)")
            .doesNotContain("prompt_family_id");
    }

    @Test
    void ai_calls_migration_adds_nullable_prompt_version_fk_and_adapter_default() throws IOException {
        String migrationText = migrationsText();

        assertThat(migrationText)
            .contains("ADD COLUMN prompt_version_id BIGINT NULL")
            .contains("ADD COLUMN provider_adapter_version VARCHAR(80) NOT NULL DEFAULT 'agent-default-v1'")
            .contains("FOREIGN KEY (prompt_version_id) REFERENCES prompt_versions(id)")
            .contains("idx_ai_calls_prompt_version");
    }

    @Test
    void trigger_request_contract_stays_on_legacy_prompt_version_for_c1() throws IOException {
        String openApi = Files.readString(Path.of("../../packages/contracts/openapi/labelhub.yaml"));

        assertThat(openApi)
            .contains("TriggerAiReviewRequest:\n      type: object\n      required: [promptVersion]")
            .doesNotContain("required: [promptVersionId]");
    }

    private String migrationsText() throws IOException {
        return Files.list(Path.of("src/main/resources/db/migration"))
            .sorted(Comparator.comparing(Path::getFileName))
            .filter(path -> path.getFileName().toString().endsWith(".sql"))
            .map(this::read)
            .collect(Collectors.joining("\n"));
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read migration " + path, exception);
        }
    }
}
