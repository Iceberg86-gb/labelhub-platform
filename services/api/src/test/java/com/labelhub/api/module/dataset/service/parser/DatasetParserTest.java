package com.labelhub.api.module.dataset.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetParserTest {

    private final DatasetParser parser = new DatasetParser(new ObjectMapper());

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

    @Test
    void parseExcel_uses_first_sheet_header_row_and_skips_blank_rows() {
        List<Map<String, Object>> items = parser.parse(workbookBytes(workbook -> {
            Row header = workbook.getSheetAt(0).createRow(0);
            header.createCell(0).setCellValue("prompt");
            header.createCell(1).setCellValue("score");
            header.createCell(2).setCellValue("active");

            Row first = workbook.getSheetAt(0).createRow(1);
            first.createCell(0).setCellValue("hello");
            first.createCell(1).setCellValue(3.5);
            first.createCell(2).setCellValue(true);

            workbook.getSheetAt(0).createRow(2);

            Row second = workbook.getSheetAt(0).createRow(3);
            second.createCell(0).setCellValue("bye");
            second.createCell(1).setCellValue(7);
            second.createCell(2).setCellValue(false);
        }), DatasetImportFormat.EXCEL);

        assertThat(items).containsExactly(
            Map.of("prompt", "hello", "score", 3.5, "active", true),
            Map.of("prompt", "bye", "score", 7.0, "active", false)
        );
    }

    @Test
    void parseExcel_rejects_duplicate_or_blank_headers() {
        assertThatThrownBy(() -> parser.parse(workbookBytes(workbook -> {
            Row header = workbook.getSheetAt(0).createRow(0);
            header.createCell(0).setCellValue("prompt");
            header.createCell(1).setCellValue("prompt");
        }), DatasetImportFormat.EXCEL))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("duplicate");
    }

    @Test
    void parseExcel_rejects_formula_cells() {
        assertThatThrownBy(() -> parser.parse(workbookBytes(workbook -> {
            Row header = workbook.getSheetAt(0).createRow(0);
            header.createCell(0).setCellValue("value");
            Row row = workbook.getSheetAt(0).createRow(1);
            Cell cell = row.createCell(0);
            cell.setCellFormula("1+1");
        }), DatasetImportFormat.EXCEL))
            .isInstanceOf(InvalidDatasetFileException.class)
            .hasMessageContaining("Formula cells are not supported");
    }

    private ByteArrayInputStream workbookBytes(WorkbookConfigurer configurer) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("items");
            configurer.configure(workbook);
            workbook.write(output);
            return new ByteArrayInputStream(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create workbook", exception);
        }
    }

    private ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    private interface WorkbookConfigurer {
        void configure(XSSFWorkbook workbook);
    }
}
