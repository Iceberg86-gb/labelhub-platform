package com.labelhub.api.module.schema.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.labelhub.api.generated.model.LinkageAtomicCondition;
import com.labelhub.api.generated.model.LinkageCondition;
import com.labelhub.api.generated.model.LinkageConditionGroup;
import java.io.IOException;

public class LinkageConditionDeserializer extends JsonDeserializer<LinkageCondition> {

    @Override
    public LinkageCondition deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode node = codec.readTree(parser);
        if (node == null || !node.isObject()) {
            return context.reportInputMismatch(LinkageCondition.class, "LinkageCondition must be a JSON object");
        }
        if (node.has("field") || node.has("op")) {
            return codec.treeToValue(node, LinkageAtomicCondition.class);
        }
        if (node.has("allOf") || node.has("anyOf")) {
            return codec.treeToValue(node, LinkageConditionGroup.class);
        }
        return context.reportInputMismatch(
            LinkageCondition.class,
            "LinkageCondition must contain field/op or allOf/anyOf"
        );
    }
}
