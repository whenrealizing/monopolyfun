package com.monopolyfun.modules.risk.domain;

public enum RiskAccountStatus {
    ACTIVE("active"),
    FROZEN("frozen"),
    BANNED("banned");

    private final String code;

    RiskAccountStatus(String code) {
        this.code = code;
    }

    public static RiskAccountStatus fromCode(String code) {
        for (RiskAccountStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown risk account status: " + code);
    }

    public String code() {
        return code;
    }
}
