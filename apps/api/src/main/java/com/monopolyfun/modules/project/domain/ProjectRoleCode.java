package com.monopolyfun.modules.project.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProjectRoleCode {
    SYSTEM_CEO,
    SYSTEM_CTO,
    SYSTEM_CFO;

    @JsonCreator
    public static ProjectRoleCode fromCode(String code) {
        for (ProjectRoleCode roleCode : values()) {
            if (roleCode.code().equalsIgnoreCase(code)) {
                return roleCode;
            }
        }
        throw new IllegalArgumentException("Unknown project role: " + code);
    }

    public boolean singleSeat() {
        return true;
    }

    @JsonValue
    public String code() {
        return name().toLowerCase();
    }
}
