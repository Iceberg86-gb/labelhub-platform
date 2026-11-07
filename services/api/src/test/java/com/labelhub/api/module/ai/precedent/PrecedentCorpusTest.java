package com.labelhub.api.module.ai.precedent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.shared.canonical.Canonicalizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ADR-021 §2-§3: tokens, text extraction, BM25 rankings and the rendered prompt block must match
 * packages/contracts/fixtures/precedent-retrieval-corpus.json. evals/tests/test_precedents.py runs the same
 * corpus against the Python mirror, so offline evals measure exactly what production retrieves.
 */
class PrecedentCorpusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Object> CORPUS = load();
    private final Canonicalizer canonicalizer = new Canonicalizer(new ObjectMapper());

    @Test
    void version_and_parameters_match() {
        assertThat(CORPUS.get("version")).isEqualTo(PrecedentBlock.VERSION);
        Map<?, ?> params = (Map<?, ?>) CORPUS.get("params");
        assertThat(params.get("k1")).isEqualTo(String.valueOf(Bm25Index.K1));
        assertThat(params.get("b")).isEqualTo(String.valueOf(Bm25Index.B));
        assertThat(params.get("excerptCodePoints")).isEqualTo(PrecedentText.EXCERPT_CODE_POINTS);
    }

    @Test
    void tokenizer_matches() {
        for (Map<String, Object> c : list("tokenize")) {
            assertThat(Bm25Index.tokenize((String) c.get("text"))).as((String) c.get("comment")).isEqualTo(c.get("tokens"));
        }
    }

    @Test
    void text_extraction_matches() {
        for (Map<String, Object> c : list("textOf")) {
            assertThat(PrecedentText.textOf(c.get("value"))).isEqualTo(c.get("text"));
            assertThat(PrecedentText.labeledText(c.get("value"))).isEqualTo(c.get("labeled"));
        }
    }

    @Test
    void rankings_match() {
        List<Bm25Index.Document<String>> docs = list("documents").stream()
            .map(d -> new Bm25Index.Document<>((String) d.get("key"), PrecedentText.textOf(d.get("content"))))
            .toList();
        Bm25Index<String> index = new Bm25Index<>(docs);
        for (Map<String, Object> q : list("queries")) {
            @SuppressWarnings("unchecked")
            List<String> exclude = (List<String>) q.get("exclude");
            assertThat(index.search((String) q.get("query"), (Integer) q.get("k"), new HashSet<>(exclude)))
                .as((String) q.get("query") + " excluding " + exclude)
                .isEqualTo(q.get("expected"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendered_block_and_hash_match() {
        Map<String, Object> render = (Map<String, Object>) CORPUS.get("render");
        List<Map<String, Object>> raw = (List<Map<String, Object>>) render.get("precedents");
        List<Precedent> precedents = new java.util.ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> p = raw.get(i);
            precedents.add(new Precedent(i + 1, (long) i, (String) p.get("verdict"), (String) p.get("source"),
                PrecedentText.excerpt(PrecedentText.labeledText(p.get("content")))));
        }
        assertThat(PrecedentBlock.render(precedents, canonicalizer)).isEqualTo(render.get("block"));
        assertThat(PrecedentBlock.hash(precedents, canonicalizer)).isEqualTo(render.get("hash"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(String key) {
        return (List<Map<String, Object>>) CORPUS.get(key);
    }

    private static Map<String, Object> load() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("pnpm-workspace.yaml"))) {
            current = current.getParent();
        }
        try {
            return MAPPER.readValue(Files.readString(current.resolve("packages/contracts/fixtures/precedent-retrieval-corpus.json")),
                new TypeReference<>() { });
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
