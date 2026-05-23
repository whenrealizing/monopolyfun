package com.monopolyfun.modules.payment.infra.okx;

import java.time.Instant;
import java.util.Map;

public record OkxOnchainPaySession(
        String paymentId,
        String paymentUrl,
        String status,
        int amountMinor,
        String asset,
        String network,
        String recipient,
        String payer,
        String txHash,
        Map<String, Object> paymentRequirements,
        String settlementId,
        String externalId,
        Instant expiresAt,
        Map<String, Object> raw
) {
}
