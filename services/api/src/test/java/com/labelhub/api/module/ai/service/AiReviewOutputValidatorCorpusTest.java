package com.labelhub.api.module.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * ADR-019: the validator must match packages/contracts/fixtures/review-output-validation-corpus.json case by
 * case (code + path, in order). evals/tests/test_review_validation.py runs the same corpus against the Python
 * mirror, so the offline evals and production can never disagree on what counts as an invalid review.
 */
class AiReviewOutputValidatorCorpusTest {

    private static final ObjectMapper DOUBLES = new ObjectMapper();
    private static final ObjectMapper BIG_DECIMALS = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final TypeReference<List<CorpusCase>> CORPUS_TYPE = new TypeReference<>() {};

    private final AiReviewOutputValidator validator = new AiReviewOutputValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusCases")
    void matchesSharedCorpus(String caseId, CorpusCase corpusCase) {
        // Same result whether Jackson reads JSON floats as Double (default) or BigDecimal.
        for (ObjectMapper mapper : List.of(DOUBLES, BIG_DECIMALS)) {
            Object output = mapper.convertValue(corpusCase.output(), Object.class);
            List<ExpectedError> actual = validator.validate(output, context(corpusCase.context())).stream()
                .map(error -> new ExpectedError(error.code(), error.path()))
                .toList();
            assertThat(actual).as(caseId + ": " + corpusCase.description()).containsExactlyElementsOf(corpusCase.expectedErrors());
        }
    }

    @Test
    void corpus_covers_every_error_code_and_every_error_has_a_message() throws IOException {
        Set<String> codes = readCorpus().stream()
            .flatMap(c -> c.expectedErrors().stream())
            .map(ExpectedError::code)
            .collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder("invalid_structure", "missing_dimension", "duplicate_dimension",
            "unknown_dimension", "unknown_field", "suggestion_score_conflict", "error_finding_on_pass");
        for (CorpusCase c : readCorpus()) {
            Object output = DOUBLES.convertValue(c.output(), Object.class);
            assertThat(validator.validate(output, context(c.context()))).allSatisfy(error -> assertThat(error.message()).isNotBlank());
        }
    }

    @Test
    void conflict_message_tells_the_model_where_its_score_routes() {
        Map<String, Object> output = Map.of("overallSuggestion", "reject", "confidence", 0.35, "summary", "",
            "dimensionScores", List.of(Map.of("dimension", "overall", "score", 0.35)), "fieldFindings", List.of());
        AiReviewOutputValidator.Context ctx = new AiReviewOutputValidator.Context(List.of("overall"), new BigDecimal("0.80"), new BigDecimal("0.20"), Set.of());
        assertThat(validator.validate(output, ctx)).singleElement().satisfies(error ->
            assertThat(error.message()).isEqualTo("overallSuggestion reject contradicts the scores: final score 0.3500 routes to manual_review "
                + "(pass >= 0.80, reject <= 0.20); change the suggestion or the scores so they agree"));
    }

    private static AiReviewOutputValidator.Context context(JsonNode node) {
        List<String> dimensions = new ArrayList<>();
        node.get("dimensions").forEach(d -> dimensions.add(d.asText()));
        Set<String> stableIds = new LinkedHashSet<>();
        node.get("stableIds").forEach(d -> stableIds.add(d.asText()));
        return new AiReviewOutputValidator.Context(dimensions, new BigDecimal(node.get("passThreshold").asText()),
            new BigDecimal(node.get("rejectThreshold").asText()), stableIds);
    }

    static Stream<Arguments> corpusCases() throws IOException {
        return readCorpus().stream().map(c -> Arguments.of(c.caseId(), c));
    }

    private static List<CorpusCase> readCorpus() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("pnpm-workspace.yaml"))) {
            current = current.getParent();
        }
        assertThat(current).as("repo root").isNotNull();
        return DOUBLES.readValue(Files.readString(current.resolve("packages/contracts/fixtures/review-output-validation-corpus.json")), CORPUS_TYPE);
    }

    private record CorpusCase(String caseId, String description, JsonNode context, JsonNode output, List<ExpectedError> expectedErrors) {}

    private record ExpectedError(String code, String path) {}
}
