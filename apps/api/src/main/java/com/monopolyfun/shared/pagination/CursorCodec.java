package com.monopolyfun.shared.pagination;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public final class CursorCodec {
    private CursorCodec() {
    }

    public static String encode(String value, String id) {
        if (value == null || value.isBlank() || id == null || id.isBlank()) {
            return null;
        }
        String payload = value + "\n" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static CursorKey decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = payload.indexOf('\n');
            if (separator <= 0 || separator == payload.length() - 1) {
                throw new IllegalArgumentException("missing cursor parts");
            }
            return new CursorKey(payload.substring(0, separator), payload.substring(separator + 1));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination cursor", exception);
        }
    }

    public static Instant instantValue(CursorKey cursor) {
        try {
            return Instant.parse(cursor.value());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination cursor", exception);
        }
    }
}
