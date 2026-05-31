package com.labelhub.api.module.dataset.service.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class DatasetParser {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public DatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> parse(InputStream stream, DatasetImportFormat format) {
        return switch (format) {
            case JSON -> parseJsonArray(stream);
            case JSONL -> parseJsonl(stream);
            case EXCEL -> parseExcel(stream);
        };
    }

    private List<Map<String, Object>> parseJsonArray(InputStream stream) {
        JsonNode root;
        try {
            root = objectMapper.readTree(stream);
        } catch (IOException exception) {
            throw new InvalidDatasetFileException("Invalid JSON dataset file");
        }
        if (root == null || !root.isArray()) {
            throw new InvalidDatasetFileException("JSON dataset file must be a JSON array");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < root.size(); index++) {
            JsonNode item = root.get(index);
            if (!item.isObject()) {
                throw new InvalidDatasetFileException("JSON dataset item " + (index + 1) + " must be an object");
            }
            items.add(objectMapper.convertValue(item, OBJECT_MAP));
        }
        return items;
    }

    private List<Map<String, Object>> parseJsonl(InputStream stream) {
        List<Map<String, Object>> items = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                JsonNode item = parseJsonlLine(trimmed, lineNumber);
                if (!item.isObject()) {
                    throw new InvalidDatasetFileException(
                        "JSONL dataset line " + lineNumber + " must be an object",
                        lineNumber
                    );
                }
                items.add(objectMapper.convertValue(item, OBJECT_MAP));
            }
        } catch (IOException exception) {
            throw new InvalidDatasetFileException("Unable to read JSONL dataset file");
        }
        if (items.isEmpty()) {
            throw new InvalidDatasetFileException("JSONL dataset file is blank");
        }
        return items;
    }

    private JsonNode parseJsonlLine(String line, int lineNumber) {
        try {
            return objectMapper.readTree(line);
        } catch (JsonProcessingException exception) {
            throw new InvalidDatasetFileException("Invalid JSONL dataset file at line " + lineNumber, lineNumber);
        }
    }

    private List<Map<String, Object>> parseExcel(InputStream stream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new InvalidDatasetFileException("Excel dataset file must contain at least one sheet");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            List<String> headers = readExcelHeaders(headerRow);
            List<Map<String, Object>> items = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Map<String, Object> item = readExcelItem(row, headers, rowIndex + 1);
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
            if (items.isEmpty()) {
                throw new InvalidDatasetFileException("Excel dataset file is blank");
            }
            return items;
        } catch (IOException exception) {
            throw new InvalidDatasetFileException("Invalid Excel dataset file");
        }
    }

    private List<String> readExcelHeaders(Row headerRow) {
        if (headerRow == null) {
            throw new InvalidDatasetFileException("Excel dataset file must include a header row");
        }
        List<String> headers = new ArrayList<>();
        Set<String> seenHeaders = new HashSet<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            Cell cell = headerRow.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String header = cell == null ? "" : cell.getStringCellValue().trim();
            if (header.isBlank()) {
                throw new InvalidDatasetFileException("Excel dataset header " + (cellIndex + 1) + " must not be blank");
            }
            if (!seenHeaders.add(header)) {
                throw new InvalidDatasetFileException("Excel dataset header '" + header + "' is duplicate");
            }
            headers.add(header);
        }
        if (headers.isEmpty()) {
            throw new InvalidDatasetFileException("Excel dataset file must include at least one header");
        }
        return headers;
    }

    private Map<String, Object> readExcelItem(Row row, List<String> headers, int rowNumber) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (row == null) {
            return item;
        }
        for (int index = 0; index < headers.size(); index++) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            Object value = readExcelValue(cell, rowNumber, index + 1);
            if (value != null) {
                item.put(headers.get(index), value);
            }
        }
        return item;
    }

    private Object readExcelValue(Cell cell, int rowNumber, int columnNumber) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> {
                String value = cell.getStringCellValue().trim();
                yield value.isBlank() ? null : value;
            }
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue().toString()
                : cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> throw new InvalidDatasetFileException(
                "Formula cells are not supported in Excel dataset files at row " + rowNumber + ", column " + columnNumber
            );
            default -> throw new InvalidDatasetFileException(
                "Unsupported Excel cell type at row " + rowNumber + ", column " + columnNumber
            );
        };
    }
}
