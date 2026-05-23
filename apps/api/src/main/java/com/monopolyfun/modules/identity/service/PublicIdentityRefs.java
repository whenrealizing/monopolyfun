package com.monopolyfun.modules.identity.service;

public final class PublicIdentityRefs {
    private PublicIdentityRefs() {
    }

    public static String accountId(String handle) {
        if (handle == null) {
            return null;
        }
        String normalized = handle.trim().replaceFirst("^@+", "").toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}
