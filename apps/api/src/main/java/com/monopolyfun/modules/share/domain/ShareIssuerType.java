package com.monopolyfun.modules.share.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ShareIssuerType {
    PROJECT;

    @JsonCreator
    public static ShareIssuerType fromCode(String code) {
        for (ShareIssuerType issuerType : values()) {
            if (issuerType.code().equalsIgnoreCase(code)) {
                return issuerType;
            }
        }
        throw new IllegalArgumentException("Unknown share issuer type: " + code);
    }

    @JsonValue
    public String code() {
        return name().toLowerCase();
    }
}
