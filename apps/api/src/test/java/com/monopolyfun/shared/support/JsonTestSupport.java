package com.monopolyfun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class JsonTestSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestSupport() {
    }

    static String readString(String json, String pointer) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(json).at(pointer);
        if (node.isMissingNode()) {
            throw new IllegalArgumentException("Missing JSON pointer: " + pointer);
        }
        return node.asText();
    }
}
