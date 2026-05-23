package com.monopolyfun.modules.payment.domain;

public enum PaymentIntentStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    REFUNDED,
    CANCELLED,
    DISPUTED,
    FAILED
}
