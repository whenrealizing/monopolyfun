package com.monopolyfun.modules.risk.domain;

public enum RiskLevel {
    NORMAL("normal"),
    WATCH("watch"),
    HIGH("high");

    private final String code;

    RiskLevel(String code) {
        this.code = code;
    }

    public static RiskLevel fromCode(String code) {
        for (RiskLevel level : values()) {
            if (level.code.equalsIgnoreCase(code)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown risk level: " + code);
    }

    public String code() {
        return code;
    }
}
