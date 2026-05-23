package com.monopolyfun.modules.payment.infra.okx;

import java.util.Map;

public record OkxOnchainPayCreateRequest(
        String orderId,
        String idempotencyKey,
        int amountMinor,
        String asset,
        String network,
        String recipient,
        String payer,
        Map<String, Object> paymentPayload,
        boolean syncSettle,
        Map<String, Object> metadata
) {
}
