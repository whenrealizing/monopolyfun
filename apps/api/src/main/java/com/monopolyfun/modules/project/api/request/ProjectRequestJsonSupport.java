package com.monopolyfun.modules.project.api.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class ProjectRequestJsonSupport {
    private ProjectRequestJsonSupport() {
    }

    static void requireObject(JsonNode node, JsonParser parser, String label) throws MismatchedInputException {
        if (node == null || !node.isObject()) {
            throw MismatchedInputException.from(parser, Object.class, label + " must be an object");
        }
    }

    static void rejectUnknownFields(JsonNode node, Set<String> allowedFields, JsonParser parser, String label) throws MismatchedInputException {
        if (node == null || !node.isObject()) {
            return;
        }
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!allowedFields.contains(field)) {
                throw MismatchedInputException.from(parser, Object.class, label + "." + field + " is not supported");
            }
        }
    }

    static void rejectField(JsonNode node, String field, JsonParser parser, String message) throws MismatchedInputException {
        if (node != null && node.has(field)) {
            throw MismatchedInputException.from(parser, Object.class, message);
        }
    }

    static String optionalText(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    static Double optionalDouble(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isNumber() ? value.doubleValue() : Double.valueOf(value.asText());
    }

    static List<String> optionalStringList(ObjectMapper mapper, JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return mapper.convertValue(value, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }
}
