package com.monopolyfun.modules.payment.service.view;

import com.fasterxml.jackson.annotation.JsonInclude;

public record PaymentBindingView(
        String orderId,
        String orderNo,
        String payerAccountId,
        String payeeAccountId,
        String fulfillerAccountId,
        String postKind,
        String postId,
        String itemId,
        String payerAddress,
        String recipientAddress,
        String paymentId,
        String txHash,
        String network,
        String asset,
        String evidenceStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String evidencePath
) {
}
