package com.labelhub.api.module.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.module.ai.provider.AiCallResult;
import com.labelhub.api.module.ai.provider.PromptTemplate;
import com.labelhub.api.module.ai.service.AiReviewScoringPolicy;
import com.labelhub.api.module.ai.service.AiReviewScoringProperties;
import com.labelhub.api.module.ai.service.ScoredAiReview;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offline eval harness (evals/) re-implements the AI review request and scoring in Python. These tests pin the
 * Java side to the shared files in packages/contracts/ai so the two cannot drift apart silently: the Python test
 * suite asserts against the very same files.
 */
class AiContractParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Contract: PromptTemplate renders the fixture to exactly the golden text (byte for byte).
    @Test
    @SuppressWarnings("unchecked")
    void prompt_template_matches_golden_rendering() throws IOException {
        JsonNode fixture = OBJECT_MAPPER.readTree(Files.readString(contract("prompt-template.fixture.json")));
        Map<String, Object> input = OBJECT_MAPPER.convertValue(fixture.get("input"), LinkedHashMap.class);

        String rendered = PromptTemplate.build(fixture.get("businessPrompt").asText(), input, OBJECT_MAPPER);

        assertThat(rendered).isEqualTo(Files.readString(contract("prompt-template.golden.txt")));
    }

    // Contract: every case of the shared scoring corpus yields the expected final score and three-zone route.
    @Test
    void scoring_policy_matches_shared_corpus() throws IOException {
        JsonNode corpus = OBJECT_MAPPER.readTree(Files.readString(contract("scoring-corpus.json")));
        AiReviewScoringProperties properties = new AiReviewScoringProperties();
        assertThat(properties.getScoringRuleVersion()).isEqualTo(corpus.get("scoringRuleVersion").asText());
        AiReviewScoringPolicy policy = new AiReviewScoringPolicy(properties);

        List<String> failures = new ArrayList<>();
        for (JsonNode testCase : corpus.get("cases")) {
            ScoredAiReview scored = policy.score(
                result(testCase),
                OBJECT_MAPPER.convertValue(testCase.get("dimensions"), List.class),
                new BigDecimal(testCase.get("passThreshold").asText()),
                new BigDecimal(testCase.get("rejectThreshold").asText())
            );
            BigDecimal expectedScore = new BigDecimal(testCase.get("expectedFinalScore").asText());
            String expectedRoute = testCase.get("expectedRecommendation").asText();
            if (scored.finalScore().compareTo(expectedScore) != 0 || !scored.recommendation().equals(expectedRoute)) {
                failures.add(testCase.get("name").asText() + ": expected " + expectedScore + "/" + expectedRoute
                    + " but was " + scored.finalScore() + "/" + scored.recommendation());
            }
        }
        assertThat(failures).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static AiCallResult result(JsonNode testCase) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("dimensionScores", OBJECT_MAPPER.convertValue(testCase.get("dimensionScores"), List.class));
        JsonNode confidence = testCase.get("confidence");
        BigDecimal confidenceValue = confidence == null || confidence.isNull() ? null : confidence.decimalValue();
        return new AiCallResult(output, "manual_review", confidenceValue, "", List.of(), 0, 0, BigDecimal.ZERO, 0L, "");
    }

    private static Path contract(String fileName) {
        return repoRoot().resolve("packages/contracts/ai").resolve(fileName);
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pnpm-workspace.yaml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Unable to find repo root from " + Path.of("").toAbsolutePath());
    }
}
