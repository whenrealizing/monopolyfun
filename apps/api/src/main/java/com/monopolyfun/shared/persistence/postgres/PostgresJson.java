package com.monopolyfun.shared.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.monopolyfun.modules.order.domain.ProofLink;
import org.jooq.EnumType;
import org.jooq.JSONB;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public final class PostgresJson {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private PostgresJson() {
    }

    public static JSONB jsonb(Object value) {
        try {
            return JSONB.valueOf(OBJECT_MAPPER.writeValueAsString(value == null ? Map.of() : value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize JSONB value", exception);
        }
    }

    public static Map<String, Object> map(JSONB value) {
        return read(value, new TypeReference<>() {
        }, Map.of());
    }

    public static List<String> stringList(JSONB value) {
        return read(value, new TypeReference<>() {
        }, List.of());
    }

    public static List<ProofLink> proofLinks(JSONB value) {
        return read(value, new TypeReference<>() {
        }, List.of());
    }

    public static <T> T jsonbValue(JSONB value, TypeReference<T> typeReference, T defaultValue) {
        return read(value, typeReference, defaultValue);
    }

    public static OffsetDateTime offsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public static <T extends Enum<T> & EnumType> T jooqEnum(Class<T> type, Enum<?> value) {
        // Repository 写入使用 jOOQ 生成枚举，schema 枚举变化会在编译期暴露。
        if (value == null) {
            return null;
        }
        return EnumType.lookupLiteral(type, value.name().toLowerCase());
    }

    public static <T extends Enum<T>> T modelEnum(Class<T> type, Object value) {
        if (value == null) {
            return null;
        }
        String literal = value instanceof EnumType enumType ? enumType.getLiteral() : String.valueOf(value);
        return Enum.valueOf(type, literal.toUpperCase());
    }

    private static <T> T read(JSONB value, TypeReference<T> typeReference, T defaultValue) {
        if (value == null || value.data() == null || value.data().isBlank() || "null".equals(value.data())) {
            return defaultValue;
        }
        try {
            return OBJECT_MAPPER.readValue(value.data(), typeReference);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to deserialize JSONB value", exception);
        }
    }
}
