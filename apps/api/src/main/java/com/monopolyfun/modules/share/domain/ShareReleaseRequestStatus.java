package com.monopolyfun.modules.share.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ShareReleaseRequestStatus {
    PENDING,
    APPROVED,
    SKIPPED;

    @JsonCreator
    public static ShareReleaseRequestStatus fromCode(String code) {
        for (ShareReleaseRequestStatus status : values()) {
            if (status.code().equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown share release request status: " + code);
    }

    @JsonValue
    public String code() {
        return name().toLowerCase();
    }
}
