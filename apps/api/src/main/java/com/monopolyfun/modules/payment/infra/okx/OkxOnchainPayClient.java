package com.monopolyfun.modules.payment.infra.okx;

import java.util.Map;

public interface OkxOnchainPayClient {
    Map<String, Object> buildPaymentRequirements(int amountMinor, String asset, String recipient);

    OkxOnchainPaySession createPayment(OkxOnchainPayCreateRequest request);

    OkxOnchainPaySession getPaymentStatus(String paymentSessionId, OkxOnchainPayCreateRequest request);
}
