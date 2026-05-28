package com.labelhub.api.module.submission.validation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.config.JacksonConfig;
import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import com.labelhub.api.module.schema.util.SchemaValidator;
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

class LinkageCorpusTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JacksonConfig().labelhubJacksonModule());
    private static final TypeReference<List<CorpusCase>> CORPUS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final AnswerPayloadValidator answerPayloadValidator = new AnswerPayloadValidator();
    private final SchemaValidator schemaValidator = new SchemaValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("runtimeCases")
    void runtimeCasesMatchSharedCorpus(String caseId, CorpusCase corpusCase) {
        SchemaDocument schema = OBJECT_MAPPER.convertValue(corpusCase.schema(), SchemaDocument.class);
        Map<String, Object> payload = OBJECT_MAPPER.convertValue(corpusCase.payload(), PAYLOAD_TYPE);

        List<ExpectedError> actual = answerPayloadValidator.validate(schema, payload).stream()
            .map(error -> new ExpectedError(error.stableId(), error.reason()))
            .toList();

        assertThat(actual)
            .as(caseId + ": " + corpusCase.description())
            .containsExactlyElementsOf(corpusCase.expectedErrors());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publishCases")
    void publishCasesMatchSharedCorpus(String caseId, CorpusCase corpusCase) {
        SchemaDocument schema = OBJECT_MAPPER.convertValue(corpusCase.schema(), SchemaDocument.class);

        List<ExpectedPublishError> actual = validateSchema(schema);

        assertThat(actual)
            .as(caseId + ": " + corpusCase.description())
            .containsExactlyElementsOf(corpusCase.expectedPublishErrors());
    }

    @Test
    void corpusHasNoKnownAsymmetries() throws IOException {
        List<String> asymmetricCaseIds = readCorpus().stream()
            .filter(corpusCase -> !corpusCase.expectSymmetry())
            .map(CorpusCase::caseId)
            .toList();

        assertThat(asymmetricCaseIds).isEmpty();
    }

    static Stream<Arguments> runtimeCases() throws IOException {
        return readCorpus().stream()
            .filter(corpusCase -> "runtime".equals(corpusCase.kind()))
            .map(corpusCase -> Arguments.of(corpusCase.caseId(), corpusCase));
    }

    static Stream<Arguments> publishCases() throws IOException {
        return readCorpus().stream()
            .filter(corpusCase -> "publish".equals(corpusCase.kind()))
            .map(corpusCase -> Arguments.of(corpusCase.caseId(), corpusCase));
    }

    private List<ExpectedPublishError> validateSchema(SchemaDocument schema) {
        try {
            schemaValidator.validate(schema);
            return List.of();
        } catch (InvalidSchemaDocumentException exception) {
            return List.of(new ExpectedPublishError(exception.getField(), exception.getReason()));
        }
    }

    private static List<CorpusCase> readCorpus() throws IOException {
        Path corpusPath = findRepoRoot().resolve("packages/contracts/fixtures/linkage-corpus.json");
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
        String kind,
        String caseId,
        String description,
        JsonNode schema,
        JsonNode payload,
        List<ExpectedError> expectedErrors,
        List<ExpectedPublishError> expectedPublishErrors,
        boolean expectSymmetry
    ) {}

    private record ExpectedError(String stableId, String reason) {}

    private record ExpectedPublishError(String fieldPath, String reason) {}
}
