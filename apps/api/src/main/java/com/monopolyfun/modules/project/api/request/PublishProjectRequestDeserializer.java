package com.monopolyfun.modules.project.api.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PublishProjectRequestDeserializer extends StdDeserializer<PublishProjectRequest> {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "title",
            "description",
            "goal",
            "parentProjectId",
            "ownerIntro",
            "provisionSessionId",
            "items");
    private static final Set<String> ALLOWED_ITEM_FIELDS = Set.of(
            "name",
            "description",
            "deliveryStandard",
            "acceptanceCriteria",
            "difficultyScore");

    public PublishProjectRequestDeserializer() {
        super(PublishProjectRequest.class);
    }

    @Override
    public PublishProjectRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode root = mapper.readTree(parser);
        ProjectRequestJsonSupport.requireObject(root, parser, "project");
        ProjectRequestJsonSupport.rejectUnknownFields(root, ALLOWED_FIELDS, parser, "project");
        return new PublishProjectRequest(
                ProjectRequestJsonSupport.optionalText(root, "title"),
                ProjectRequestJsonSupport.optionalText(root, "description"),
                ProjectRequestJsonSupport.optionalText(root, "goal"),
                ProjectRequestJsonSupport.optionalText(root, "parentProjectId"),
                ProjectRequestJsonSupport.optionalText(root, "ownerIntro"),
                ProjectRequestJsonSupport.optionalText(root, "provisionSessionId"),
                parseItems(mapper, parser, root.get("items")));
    }

    private List<PublishProjectItemRequest> parseItems(ObjectMapper mapper, JsonParser parser, JsonNode itemsNode) throws IOException {
        if (itemsNode == null || itemsNode.isNull()) {
            // 中文注释：Project 发布必须从业务语义上携带初始任务，避免 agent 只创建空公司。
            throw MismatchedInputException.from(parser, List.class, "project.items is required as initial tasks and must contain at least one task");
        }
        if (!itemsNode.isArray()) {
            throw MismatchedInputException.from(parser, List.class, "project.items must be an array");
        }
        if (itemsNode.isEmpty()) {
            throw MismatchedInputException.from(parser, List.class, "project.items must contain at least one initial task");
        }
        List<PublishProjectItemRequest> items = new ArrayList<>();
        for (int index = 0; index < itemsNode.size(); index++) {
            JsonNode itemNode = itemsNode.get(index);
            ProjectRequestJsonSupport.requireObject(itemNode, parser, "project.items[" + index + "]");
            ProjectRequestJsonSupport.rejectField(itemNode, "amount", parser, "Project items[" + index + "].amount is system-priced and must not be provided");
            ProjectRequestJsonSupport.rejectField(itemNode, "agentInstruction", parser, "Project items[" + index + "].agentInstruction is derived from deliveryStandard and must not be provided");
            ProjectRequestJsonSupport.rejectField(itemNode, "quantity", parser, "Project items[" + index + "].quantity must be 1");
            ProjectRequestJsonSupport.rejectUnknownFields(itemNode, ALLOWED_ITEM_FIELDS, parser, "project.items[" + index + "]");
            items.add(new PublishProjectItemRequest(
                    ProjectRequestJsonSupport.optionalText(itemNode, "name"),
                    ProjectRequestJsonSupport.optionalText(itemNode, "description"),
                    ProjectRequestJsonSupport.optionalText(itemNode, "deliveryStandard"),
                    ProjectRequestJsonSupport.optionalStringList(mapper, itemNode, "acceptanceCriteria"),
                    ProjectRequestJsonSupport.optionalDouble(itemNode, "difficultyScore")));
        }
        return items;
    }
}
