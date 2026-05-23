package com.monopolyfun.modules.order.service.view;

public record ActionView(
        String id,
        String label,
        String method,
        String href,
        String importance,
        String role,
        String reasonCode,
        String disabledReason,
        boolean requiresPayment,
        boolean requiresProof,
        String dangerLevel
) {
}
