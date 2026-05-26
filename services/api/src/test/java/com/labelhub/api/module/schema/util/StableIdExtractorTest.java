package com.labelhub.api.module.schema.util;

import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaFieldType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StableIdExtractorTest {

    private final StableIdExtractor extractor = new StableIdExtractor();

    @Test
    void extract_returns_empty_for_empty_document() {
        SchemaDocument document = new SchemaDocument();
        document.setFields(List.of());

        assertThat(extractor.extract(document)).isEmpty();
        assertThat(extractor.extract(null)).isEmpty();
    }

    @Test
    void extract_flattens_top_level_fields() {
        assertThat(extractor.extract(document(
                field("field-a"),
                field("field-b"),
                field("field-c"))))
                .containsExactly("field-a", "field-b", "field-c");
    }

    @Test
    void extract_recurses_into_nested_object_children() {
        SchemaField nested = field("parent");
        nested.setType(SchemaFieldType.NESTED_OBJECT);
        SchemaField childNested = field("child-parent");
        childNested.setType(SchemaFieldType.NESTED_OBJECT);
        childNested.setChildren(List.of(field("grandchild")));
        nested.setChildren(List.of(field("child-a"), childNested, field("child-b")));

        assertThat(extractor.extract(document(field("top"), nested)))
                .containsExactly("top", "parent", "child-a", "child-parent", "grandchild", "child-b");
    }

    private static SchemaDocument document(SchemaField... fields) {
        SchemaDocument document = new SchemaDocument();
        document.setFields(List.of(fields));
        return document;
    }

    private static SchemaField field(String stableId) {
        SchemaField field = new SchemaField();
        field.setStableId(stableId);
        field.setLabel(stableId);
        field.setType(SchemaFieldType.TEXT);
        return field;
    }
}
