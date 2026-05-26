package com.labelhub.api.module.dataset.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetParserTest {

    private DatasetParser parser;

    @BeforeEach
    void setUp() {
        parser = new DatasetParser(new ObjectMapper());
    }

    @Test
    void parseJsonArray_returns_items_in_order() {
        List<Map<String, Object>> items = parser.parse(stream("[{\"text\":\"a\"},{\"text\":\"b\"}]"), DatasetImportFormat.JSON);

        assertThat(items).extracting(item -> item.get("text")).containsExactly("a", "b");
    }

    @Test
    void parseJsonArray_rejects_top_level_object() {
        assertThatThrownBy(() -> parser.parse(stream("{\"text\":\"a\"}"), DatasetImportFormat.JSON))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("JSON array");
    }

    @Test
    void parseJsonArray_rejects_non_object_element() {
        assertThatThrownBy(() -> parser.parse(stream("[{\"text\":\"a\"},\"bad\"]"), DatasetImportFormat.JSON))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("item 2");
    }

    @Test
    void parseJsonArray_rejects_malformed_json() {
        assertThatThrownBy(() -> parser.parse(stream("[{\"text\":\"a\"}"), DatasetImportFormat.JSON))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("Invalid JSON");
    }

    @Test
    void parseJsonl_returns_items_in_line_order() {
        List<Map<String, Object>> items = parser.parse(stream("{\"text\":\"a\"}\n{\"text\":\"b\"}\n"), DatasetImportFormat.JSONL);

        assertThat(items).extracting(item -> item.get("text")).containsExactly("a", "b");
    }

    @Test
    void parseJsonl_rejects_malformed_line_with_line_number() {
        assertThatThrownBy(() -> parser.parse(stream("{\"text\":\"a\"}\nnot-json\n"), DatasetImportFormat.JSONL))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("line 2");
    }

    @Test
    void parseJsonl_rejects_non_object_line() {
        assertThatThrownBy(() -> parser.parse(stream("{\"text\":\"a\"}\n[1,2]\n"), DatasetImportFormat.JSONL))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("line 2");
    }

    @Test
    void parseJsonl_skips_empty_trailing_lines() {
        List<Map<String, Object>> items = parser.parse(stream("{\"text\":\"a\"}\n\n"), DatasetImportFormat.JSONL);

        assertThat(items).hasSize(1);
    }

    @Test
    void parseJsonl_rejects_blank_input() {
        assertThatThrownBy(() -> parser.parse(stream("\n\n"), DatasetImportFormat.JSONL))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("blank");
    }

    private ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
