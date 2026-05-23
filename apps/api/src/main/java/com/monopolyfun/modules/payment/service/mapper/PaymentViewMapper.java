package com.monopolyfun.modules.payment.service.mapper;

import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.payment.service.view.PaymentBindingView;
import com.monopolyfun.modules.payment.service.view.PaymentIntentView;

import java.util.Map;

public final class PaymentViewMapper {
    private PaymentViewMapper() {
    }

    public static PaymentIntentView paymentIntent(PaymentIntentEntity paymentIntent) {
        if (paymentIntent == null) return null;
        return new PaymentIntentView(
                paymentIntent.id(),
                paymentIntent.paymentNo(),
                paymentIntent.orderId(),
                paymentIntent.accountId(),
                paymentIntent.provider(),
                paymentIntent.providerPaymentRef(),
                paymentIntent.status(),
                paymentIntent.amountMinor(),
                paymentIntent.currency(),
                paymentIntent.authorizedAt(),
                paymentIntent.capturedAt(),
                paymentIntent.refundedAt(),
                paymentIntent.cancelledAt(),
                paymentIntent.disputedAt(),
                paymentIntent.metadata(),
                binding(paymentIntent),
                paymentIntent.createdAt(),
                paymentIntent.updatedAt());
    }

    private static PaymentBindingView binding(PaymentIntentEntity paymentIntent) {
        Map<String, Object> metadata = paymentIntent.metadata() == null ? Map.of() : paymentIntent.metadata();
        return new PaymentBindingView(
                stringValue(metadata.get("orderId"), paymentIntent.orderId()),
                stringValue(metadata.get("orderNo"), null),
                stringValue(metadata.get("payerAccountId"), paymentIntent.accountId()),
                stringValue(metadata.get("payeeAccountId"), null),
                stringValue(metadata.get("fulfillerAccountId"), null),
                stringValue(metadata.get("postKind"), null),
                stringValue(metadata.get("postId"), null),
                stringValue(metadata.get("itemId"), null),
                stringValue(metadata.get("payerAddress"), stringValue(metadata.get("payer"), null)),
                stringValue(metadata.get("recipientAddress"), stringValue(metadata.get("recipient"), stringValue(metadata.get("receiver"), null))),
                stringValue(metadata.get("paymentId"), paymentIntent.providerPaymentRef()),
                stringValue(metadata.get("txHash"), null),
                stringValue(metadata.get("network"), stringValue(metadata.get("paymentNetwork"), null)),
                stringValue(metadata.get("asset"), stringValue(metadata.get("paymentAsset"), paymentIntent.currency())),
                evidenceStatus(paymentIntent, metadata),
                stringValue(metadata.get("okxA2aEvidencePath"), stringValue(metadata.get("evidencePath"), null)));
    }

    private static String evidenceStatus(PaymentIntentEntity paymentIntent, Map<String, Object> metadata) {
        if (metadata.containsKey("txHash")) {
            return "chain_evidence_attached";
        }
        if (metadata.containsKey("paymentId")) {
            return "payment_id_bound";
        }
        return paymentIntent.status().name().toLowerCase();
    }

    private static String stringValue(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return fallback;
    }
}
