package com.monopolyfun.modules.projection;

import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

@Component
public class OrderPaymentStateWriter {
    private final DSLContext dsl;
    private final PostItemProjectionWriter postItemProjectionWriter;

    public OrderPaymentStateWriter(DSLContext dsl, PostItemProjectionWriter postItemProjectionWriter) {
        this.dsl = dsl;
        this.postItemProjectionWriter = postItemProjectionWriter;
    }

    public void upsert(PaymentIntentEntity paymentIntent) {
        // 中文注释：订单当前支付态是跨模块读模型，由统一 writer 维护最新支付事实。
        dsl.execute("""
                        INSERT INTO order_payment_state (
                          order_id, latest_payment_intent_id, status, amount_minor, currency, provider, provider_payment_ref,
                          authorized_at, captured_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                        ON CONFLICT (order_id) DO UPDATE
                        SET latest_payment_intent_id = EXCLUDED.latest_payment_intent_id,
                            status = EXCLUDED.status,
                            amount_minor = EXCLUDED.amount_minor,
                            currency = EXCLUDED.currency,
                            provider = EXCLUDED.provider,
                            provider_payment_ref = EXCLUDED.provider_payment_ref,
                            authorized_at = EXCLUDED.authorized_at,
                            captured_at = EXCLUDED.captured_at,
                            updated_at = EXCLUDED.updated_at
                        """,
                paymentIntent.orderId(),
                paymentIntent.id(),
                paymentIntent.status().name().toLowerCase(),
                paymentIntent.amountMinor(),
                paymentIntent.currency(),
                paymentIntent.provider(),
                paymentIntent.providerPaymentRef(),
                PostgresJson.offsetDateTime(paymentIntent.authorizedAt()),
                PostgresJson.offsetDateTime(paymentIntent.capturedAt()),
                PostgresJson.offsetDateTime(paymentIntent.updatedAt()));
        postItemProjectionWriter.syncPaymentState(paymentIntent);
    }
}
