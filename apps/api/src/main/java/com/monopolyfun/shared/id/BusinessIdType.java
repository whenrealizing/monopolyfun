package com.monopolyfun.shared.id;

public enum BusinessIdType {
    OFFER("offer", "OFF"),
    REQUEST("request", "REQ"),
    PROJECT("project", "PRJ"),
    ORDER("order", "ORD"),
    PAYMENT("pay", "PAY");

    private final String internalPrefix;
    private final String displayCode;

    BusinessIdType(String internalPrefix, String displayCode) {
        this.internalPrefix = internalPrefix;
        this.displayCode = displayCode;
    }

    public String internalPrefix() {
        return internalPrefix;
    }

    public String displayCode() {
        return displayCode;
    }
}
