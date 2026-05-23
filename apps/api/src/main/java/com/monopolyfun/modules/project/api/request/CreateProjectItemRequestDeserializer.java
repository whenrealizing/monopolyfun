package com.monopolyfun.modules.project.api.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.Set;

public class CreateProjectItemRequestDeserializer extends StdDeserializer<CreateProjectItemRequest> {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "actorAccountId",
            "name",
            "description",
            "deliveryStandard",
            "acceptanceCriteria",
            "difficultyScore",
            "itemType",
            "mode");

    public CreateProjectItemRequestDeserializer() {
        super(CreateProjectItemRequest.class);
    }

    @Override
    public CreateProjectItemRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode root = mapper.readTree(parser);
        ProjectRequestJsonSupport.requireObject(root, parser, "project item");
        ProjectRequestJsonSupport.rejectField(root, "amount", parser, "Project item amount is system-priced and must not be provided");
        ProjectRequestJsonSupport.rejectField(root, "agentInstruction", parser, "Project item agentInstruction is derived from deliveryStandard and must not be provided");
        ProjectRequestJsonSupport.rejectField(root, "quantity", parser, "Project item quantity must be 1");
        ProjectRequestJsonSupport.rejectUnknownFields(root, ALLOWED_FIELDS, parser, "project item");
        return new CreateProjectItemRequest(
                ProjectRequestJsonSupport.optionalText(root, "actorAccountId"),
                ProjectRequestJsonSupport.optionalText(root, "name"),
                ProjectRequestJsonSupport.optionalText(root, "description"),
                ProjectRequestJsonSupport.optionalText(root, "deliveryStandard"),
                ProjectRequestJsonSupport.optionalStringList(mapper, root, "acceptanceCriteria"),
                ProjectRequestJsonSupport.optionalDouble(root, "difficultyScore"),
                ProjectRequestJsonSupport.optionalText(root, "itemType"),
                ProjectRequestJsonSupport.optionalText(root, "mode"));
    }
}
