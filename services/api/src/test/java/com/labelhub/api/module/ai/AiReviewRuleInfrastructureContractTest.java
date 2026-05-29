package com.labelhub.api.module.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewRuleInfrastructureContractTest {

    @Test
    void ai_review_rules_migration_adds_task_scoped_rule_versions_and_task_pointer() throws IOException {
        String migrations = migrationsText();

        assertThat(migrations)
            .contains("CREATE TABLE ai_review_rules")
            .contains("task_id BIGINT NOT NULL")
            .contains("version_no INT NOT NULL")
            .contains("current_prompt_version_id BIGINT NOT NULL")
            .contains("dimensions_json JSON NOT NULL")
            .contains("threshold DECIMAL(8,4) NOT NULL")
            .contains("status VARCHAR(32) NOT NULL DEFAULT 'draft'")
            .contains("created_by BIGINT NOT NULL")
            .contains("activated_at DATETIME(3)")
            .contains("FOREIGN KEY (task_id) REFERENCES tasks(id)")
            .contains("FOREIGN KEY (current_prompt_version_id) REFERENCES prompt_versions(id)")
            .contains("UNIQUE KEY uk_ai_review_rules_task_version (task_id, version_no)");

        assertThat(migrations)
            .contains("ADD COLUMN current_ai_review_rule_id BIGINT NULL")
            .contains("FOREIGN KEY (current_ai_review_rule_id) REFERENCES ai_review_rules(id)");
    }

    @Test
    void ai_review_rule_openapi_response_is_explicit_and_keeps_request_prompt_template_input() throws IOException {
        String openApi = Files.readString(Path.of("../../packages/contracts/openapi/labelhub.yaml"));

        assertThat(openApi)
            .contains("/ai-review/rules/{ruleId}/publish:")
            .contains("operationId: publishAiReviewRule")
            .contains("AiReviewRuleRequest:\n      type: object\n      required: [taskId, promptTemplate, dimensions, threshold]")
            .contains("AiReviewRuleStatus:")
            .contains("enum: [draft, published]")
            .contains("AiReviewRule:\n      type: object")
            .contains("required: [id, taskId, versionNo, promptVersionId, promptTemplate, dimensions, threshold, status, isCurrent, createdAt]")
            .contains("promptVersionId:")
            .contains("versionNo:")
            .contains("isCurrent:")
            .contains("activatedAt:");

        assertThat(openApi).doesNotContain("conclusionStrategy");
    }

    @Test
    void ai_review_rule_read_contract_lists_rules_with_shared_errors() throws IOException {
        String openApi = Files.readString(Path.of("../../packages/contracts/openapi/labelhub.yaml"));

        assertThat(openApi)
            .contains("/ai-review/rules:")
            .contains("operationId: listAiReviewRules")
            .contains("name: taskId")
            .contains("required: true")
            .contains("$ref: '#/components/schemas/AiReviewRule'")
            .contains("$ref: '#/components/responses/ErrorBadRequest'")
            .contains("$ref: '#/components/responses/ErrorUnauthorized'")
            .contains("$ref: '#/components/responses/ErrorForbidden'")
            .contains("$ref: '#/components/responses/ErrorNotFound'");
    }

    @Test
    void ai_review_rule_write_contract_documents_expected_error_responses() throws IOException {
        String openApi = Files.readString(Path.of("../../packages/contracts/openapi/labelhub.yaml"));

        assertThat(openApi)
            .contains("operationId: saveAiReviewRule")
            .contains("operationId: publishAiReviewRule")
            .contains("$ref: '#/components/responses/ErrorBadRequest'")
            .contains("$ref: '#/components/responses/ErrorUnauthorized'")
            .contains("$ref: '#/components/responses/ErrorForbidden'")
            .contains("$ref: '#/components/responses/ErrorNotFound'");
    }

    @Test
    void ai_calls_migration_and_openapi_expose_ai_review_rule_evidence_binding() throws IOException {
        String migrations = migrationsText();
        String openApi = Files.readString(Path.of("../../packages/contracts/openapi/labelhub.yaml"));

        assertThat(migrations)
            .contains("ADD COLUMN ai_review_rule_id BIGINT NULL")
            .contains("idx_ai_calls_ai_review_rule")
            .contains("FOREIGN KEY (ai_review_rule_id) REFERENCES ai_review_rules(id)");

        assertThat(openApi)
            .contains("AiCall:\n      type: object")
            .contains("aiReviewRuleId:");
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
