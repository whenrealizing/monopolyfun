package com.monopolyfun.shared.validation;

import com.monopolyfun.modules.order.domain.ProofLink;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class RequestPayloadLimits {
    private static final int MAX_JSON_LIST_ITEMS = 50;

    private RequestPayloadLimits() {
    }

    public static void requireTextLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw badRequest(field + " cannot exceed " + maxLength + " characters");
        }
    }

    public static void requireListSize(String field, Collection<?> values, int maxItems) {
        if (values != null && values.size() > maxItems) {
            throw badRequest(field + " cannot exceed " + maxItems + " items");
        }
    }

    public static void requireStringList(String field, List<String> values, int maxItems, int maxLength) {
        requireListSize(field, values, maxItems);
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value != null && value.length() > maxLength) {
                throw badRequest(field + "[" + index + "] cannot exceed " + maxLength + " characters");
            }
        }
    }

    public static void requireProofLinks(String field, List<ProofLink> links, int maxItems, int maxTextLength, int maxHrefLength) {
        requireListSize(field, links, maxItems);
        if (links == null) {
            return;
        }
        for (int index = 0; index < links.size(); index++) {
            ProofLink link = links.get(index);
            if (link == null) {
                throw badRequest(field + "[" + index + "] is required");
            }
            requireTextLength(field + "[" + index + "].label", link.label(), maxTextLength);
            requireTextLength(field + "[" + index + "].href", link.href(), maxHrefLength);
            validateHttpUrl(field + "[" + index + "].href", link.href());
        }
    }

    public static void requireMapShape(String field, Map<String, Object> value, int maxDepth, int maxEntries, int maxStringLength) {
        if (value == null) {
            return;
        }
        // 中文注释：结构化 payload 统一限制深度、键数量和字符串长度，保护数据库 JSONB 与审计日志。
        int entries = validateJsonValue(field, value, 0, maxDepth, maxEntries, maxStringLength, 0);
        if (entries > maxEntries) {
            throw badRequest(field + " cannot exceed " + maxEntries + " entries");
        }
    }

    private static int validateJsonValue(
            String field,
            Object value,
            int depth,
            int maxDepth,
            int maxEntries,
            int maxStringLength,
            int entryCount) {
        if (depth > maxDepth) {
            throw badRequest(field + " exceeds max depth " + maxDepth);
        }
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return entryCount;
        }
        if (value instanceof String text) {
            requireTextLength(field, text, maxStringLength);
            return entryCount;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw badRequest(field + " keys must be non-empty strings");
                }
                requireTextLength(field + ".key", key, 80);
                entryCount++;
                if (entryCount > maxEntries) {
                    throw badRequest(field + " cannot exceed " + maxEntries + " entries");
                }
                entryCount = validateJsonValue(field + "." + key, entry.getValue(), depth + 1, maxDepth, maxEntries, maxStringLength, entryCount);
            }
            return entryCount;
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_JSON_LIST_ITEMS) {
                throw badRequest(field + " lists cannot exceed " + MAX_JSON_LIST_ITEMS + " items");
            }
            for (int index = 0; index < list.size(); index++) {
                entryCount = validateJsonValue(field + "[" + index + "]", list.get(index), depth + 1, maxDepth, maxEntries, maxStringLength, entryCount);
            }
            return entryCount;
        }
        throw badRequest(field + " contains unsupported JSON value");
    }

    private static void validateHttpUrl(String field, String value) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " is required");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw badRequest(field + " must be an http or https URL");
            }
        } catch (URISyntaxException exception) {
            throw badRequest(field + " must be a valid URL");
        }
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
