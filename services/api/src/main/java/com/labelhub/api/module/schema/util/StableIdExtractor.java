package com.labelhub.api.module.schema.util;

import com.labelhub.api.generated.model.SchemaDocument;
import com.labelhub.api.generated.model.SchemaField;
import com.labelhub.api.generated.model.SchemaTab;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StableIdExtractor {

    public List<String> extract(SchemaDocument document) {
        List<String> ids = new ArrayList<>();
        if (document == null || document.getFields() == null) {
            return ids;
        }
        collect(document.getFields(), ids);
        return ids;
    }

    private void collect(List<SchemaField> fields, List<String> ids) {
        for (SchemaField field : fields) {
            ids.add(field.getStableId());
            if (field.getChildren() != null && !field.getChildren().isEmpty()) {
                collect(field.getChildren(), ids);
            }
            if (field.getTabs() != null && !field.getTabs().isEmpty()) {
                for (SchemaTab tab : field.getTabs()) {
                    collect(tab.getChildren(), ids);
                }
            }
        }
    }
}
