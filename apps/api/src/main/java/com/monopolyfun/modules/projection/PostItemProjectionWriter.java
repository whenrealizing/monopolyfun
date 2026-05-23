package com.monopolyfun.modules.projection;

import com.monopolyfun.modules.order.domain.OrderEntity;
import com.monopolyfun.modules.payment.domain.PaymentIntentEntity;
import com.monopolyfun.modules.post.domain.ListingEntity;
import com.monopolyfun.modules.post.domain.ListingKind;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PostItemProjectionWriter {
    private final DSLContext dsl;

    public PostItemProjectionWriter(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void syncOrderState(OrderEntity order) {
        // 中文注释：Post item 最新订单态集中写入读模型，避免订单、支付等模块各自维护 SQL。
        dsl.execute("""
                        INSERT INTO post_items_read_model (
                          item_id, post_kind, post_id, market_id, listing_id, item_kind, fulfillment_mode, delivery_mode,
                          listing_status, latest_order_id, latest_order_status, latest_order_display_phase,
                          latest_payment_intent_id, latest_payment_status, sort_at, updated_at, payload
                        )
                        SELECT
                          listing.id,
                          lower(listing.metadata->>'postKind'),
                          listing.metadata->>'postId',
                          listing.market_id,
                          listing.id,
                          listing.metadata->>'itemKind',
                          listing.metadata->>'fulfillmentMode',
                          listing.metadata->>'deliveryMode',
                          listing.status::text,
                          ?,
                          ?,
                          ?,
                          payment.latest_payment_intent_id,
                          payment.status,
                          ?::timestamptz,
                          ?::timestamptz,
                          jsonb_build_object('title', listing.title, 'subjectType', listing.subject_type, 'settlementType', listing.settlement_type::text, 'metadata', listing.metadata)
                        FROM listings listing
                        LEFT JOIN order_payment_state payment ON payment.order_id = ?
                        WHERE listing.id = ?
                          AND listing.kind = 'work'
                          AND listing.subject_type = 'post_item'
                          AND jsonb_exists(listing.metadata, 'postKind')
                          AND jsonb_exists(listing.metadata, 'postId')
                        ON CONFLICT (item_id) DO UPDATE
                        SET latest_order_id = EXCLUDED.latest_order_id,
                            latest_order_status = EXCLUDED.latest_order_status,
                            latest_order_display_phase = EXCLUDED.latest_order_display_phase,
                            latest_payment_intent_id = EXCLUDED.latest_payment_intent_id,
                            latest_payment_status = EXCLUDED.latest_payment_status,
                            sort_at = EXCLUDED.sort_at,
                            updated_at = EXCLUDED.updated_at
                        WHERE post_items_read_model.latest_order_id IS NULL
                           OR post_items_read_model.sort_at <= EXCLUDED.sort_at
                        """,
                order.id(),
                order.status().name().toLowerCase(),
                order.displayPhase(),
                PostgresJson.offsetDateTime(order.updatedAt()),
                PostgresJson.offsetDateTime(order.updatedAt()),
                order.id(),
                order.listingId());
    }

    public void syncPaymentState(PaymentIntentEntity paymentIntent) {
        dsl.execute("""
                        UPDATE post_items_read_model item
                        SET latest_payment_intent_id = ?,
                            latest_payment_status = ?,
                            updated_at = ?::timestamptz
                        FROM orders order_row
                        WHERE order_row.id = ?
                          AND item.item_id = order_row.listing_id
                          AND item.latest_order_id = order_row.id
                        """,
                paymentIntent.id(),
                paymentIntent.status().name().toLowerCase(),
                PostgresJson.offsetDateTime(paymentIntent.updatedAt()),
                paymentIntent.orderId());
    }

    public void syncListingState(ListingEntity listing) {
        if (listing.kind() != ListingKind.WORK || !"post_item".equalsIgnoreCase(listing.subjectType())) {
            return;
        }
        Object postKind = listing.metadata().get("postKind");
        Object postId = listing.metadata().get("postId");
        if (postKind == null || postId == null) {
            return;
        }
        // 中文注释：Listing 基础快照集中写入 Post item 读模型，订单和支付 writer 只覆盖动态状态列。
        dsl.execute("""
                        INSERT INTO post_items_read_model (
                          item_id, post_kind, post_id, market_id, listing_id, item_kind, fulfillment_mode, delivery_mode,
                          listing_status, sort_at, search_text, updated_at, payload
                        )
                        VALUES (?, lower(?), ?, ?, ?, ?, ?, ?, ?, ?::timestamptz, ?, ?::timestamptz, ?::jsonb)
                        ON CONFLICT (item_id) DO UPDATE
                        SET post_kind = EXCLUDED.post_kind,
                            post_id = EXCLUDED.post_id,
                            market_id = EXCLUDED.market_id,
                            listing_id = EXCLUDED.listing_id,
                            item_kind = EXCLUDED.item_kind,
                            fulfillment_mode = EXCLUDED.fulfillment_mode,
                            delivery_mode = EXCLUDED.delivery_mode,
                            listing_status = EXCLUDED.listing_status,
                            search_text = EXCLUDED.search_text,
                            updated_at = EXCLUDED.updated_at,
                            payload = EXCLUDED.payload
                        """,
                listing.id(),
                String.valueOf(postKind),
                String.valueOf(postId),
                listing.marketId(),
                listing.id(),
                listing.metadata().get("itemKind"),
                listing.metadata().get("fulfillmentMode"),
                listing.metadata().get("deliveryMode"),
                listing.status().name().toLowerCase(),
                PostgresJson.offsetDateTime(listing.updatedAt()),
                searchText(listing.title(), listing.deliverableSpec(), listing.proofSpec(), String.valueOf(listing.metadata())),
                PostgresJson.offsetDateTime(listing.updatedAt()),
                PostgresJson.jsonb(Map.of(
                        "title", listing.title(),
                        "subjectType", listing.subjectType(),
                        "settlementType", listing.settlementType().name().toLowerCase(),
                        "metadata", listing.metadata())).data());
    }

    private String searchText(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }
}
