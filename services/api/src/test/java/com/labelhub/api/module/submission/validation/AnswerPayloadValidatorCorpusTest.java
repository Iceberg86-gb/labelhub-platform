package com.labelhub.api.module.submission.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.SchemaDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerPayloadValidatorCorpusTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<CorpusCase>> CORPUS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final AnswerPayloadValidator validator = new AnswerPayloadValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpusCases")
    void validatesSharedCorpus(String caseId, CorpusCase corpusCase) {
        SchemaDocument schema = OBJECT_MAPPER.convertValue(corpusCase.schema(), SchemaDocument.class);
        Map<String, Object> payload = OBJECT_MAPPER.convertValue(corpusCase.payload(), PAYLOAD_TYPE);

        List<ExpectedError> actual = validator.validate(schema, payload).stream()
            .map(error -> new ExpectedError(error.stableId(), error.reason()))
            .toList();

        assertThat(actual)
            .as(caseId + ": " + corpusCase.description())
            .containsExactlyElementsOf(corpusCase.expectedErrors());
    }

    @Test
    void corpus_has_exactly_one_known_asymmetry() throws IOException {
        List<String> asymmetricCaseIds = readCorpus().stream()
            .filter(corpusCase -> !corpusCase.expectSymmetry())
            .map(CorpusCase::caseId)
            .toList();

        assertThat(asymmetricCaseIds).containsExactly("number-min-scientific-known-asymmetry");
    }

    static Stream<Arguments> corpusCases() throws IOException {
        return readCorpus().stream()
            .map(corpusCase -> Arguments.of(corpusCase.caseId(), corpusCase));
    }

    private static List<CorpusCase> readCorpus() throws IOException {
        Path corpusPath = findRepoRoot().resolve("packages/contracts/fixtures/validation-corpus.json");
        return OBJECT_MAPPER.readValue(Files.readString(corpusPath), CORPUS_TYPE);
    }

    private static Path findRepoRoot() {
        Path current = Path.of("").toAbsolutePath();
        List<Path> searched = new ArrayList<>();
        while (current != null) {
            searched.add(current);
            if (Files.exists(current.resolve("pnpm-workspace.yaml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Unable to find repo root. Searched: " + searched);
    }

    private record CorpusCase(
        String caseId,
        String description,
        JsonNode schema,
        JsonNode payload,
        List<ExpectedError> expectedErrors,
        boolean expectSymmetry
    ) {}

    private record ExpectedError(String stableId, String reason) {}
}
