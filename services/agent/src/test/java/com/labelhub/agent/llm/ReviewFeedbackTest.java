package com.labelhub.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.agent.api.ReviewValidationError;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR-019: the correction prompt is byte-identical to the shared fixture (and therefore to the evals mirror). */
class ReviewFeedbackTest {

    @Test
    void renders_exactly_the_shared_fixture() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("pnpm-workspace.yaml"))) {
            root = root.getParent();
        }
        assertThat(root).isNotNull();
        JsonNode fixture = new ObjectMapper().readTree(Files.readString(root.resolve("packages/contracts/fixtures/review-loop-feedback.json")));
        List<ReviewValidationError> errors = new ArrayList<>();
        fixture.get("example").get("errors").forEach(e -> errors.add(
            new ReviewValidationError(e.get("code").asText(), e.get("path").asText(), e.get("message").asText())));

        assertThat(ReviewFeedback.PROMPT_VERSION).isEqualTo(fixture.get("promptVersion").asText());
        assertThat(ReviewFeedback.HEADER).isEqualTo(fixture.get("header").asText());
        assertThat(ReviewFeedback.FOOTER).isEqualTo(fixture.get("footer").asText());
        assertThat(ReviewFeedback.render(errors)).isEqualTo(fixture.get("example").get("rendered").asText());
    }
}
