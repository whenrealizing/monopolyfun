package com.monopolyfun;

import com.monopolyfun.modules.digitalinventory.service.DigitalInventoryCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DigitalDeliveryRevealApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DigitalInventoryCrypto crypto;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("""
                truncate table delivery_attempts, digital_inventory_items, payment_provider_events,
                payment_intents, order_events, proofs, orders, listings, markets, accounts cascade
                """);
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values
                ('acct-seller', '@seller', 'Seller', '{}'::jsonb),
                ('acct-buyer', '@buyer', 'Buyer', '{}'::jsonb),
                ('acct-stranger', '@stranger', 'Stranger', '{}'::jsonb)
                """);
        jdbcTemplate.update("""
                insert into markets (
                  id, name, summary, listing_goal, lead_account_id, source_ref, surface_url,
                  settlement_type, next_curve_slot, status, lead_last_active_at, lead_seat_status, metadata, created_at, updated_at
                ) values (
                  'mkt-digital', 'Digital delivery market', 'Digital delivery market', 'Sell digital inventory',
                  'acct-seller', 'offer://offer-digital', 'http://localhost:3000/market/offers/MF260512OFF000001X',
                  'money'::settlement_type, 0, 'active'::market_status, now(), 'occupied',
                  '{}'::jsonb, now(), now()
                )
                """);
        jdbcTemplate.update("""
                insert into listings (
                  id, market_id, kind, parent_order_id, title, subject_type, subject_ref, deliverable_spec,
                  proof_spec, settlement_spec, inventory_limit, active_orders_count, stock_total,
                  settlement_type, status, opened_by_account_id, metadata, created_at, updated_at
                ) values (
                  'listing-digital', 'mkt-digital', 'work'::listing_kind, null, 'Digital license', 'post_item', 'offer-digital',
                  '系统自动交付数字库存。', '买家可 reveal 明文。', 'OKX Direct Pay',
                  1, 0, 1, 'money'::settlement_type, 'open'::listing_status, 'acct-seller',
                  '{"postKind":"offer","postId":"offer-digital","fulfillmentMode":"stock_fulfillment","deliveryMode":"stock_fulfillment"}'::jsonb,
                  now(), now()
                )
                """);
        jdbcTemplate.update("""
                insert into orders (
                  id, order_no, market_id, listing_id, kind, post_kind, post_id, parent_order_id, status, display_phase,
                  claimed_by_account_id, submitted_by_account_id, accepted_by_account_id, proof_id, settlement_type, settlement_amount,
                  closed_reason, dispute_reason, review_listing_id, delivery_snapshot, settlement_snapshot, metadata,
                  created_at, updated_at
                ) values (
                  'order-digital', 'MF260512ORD000001X', 'mkt-digital', 'listing-digital', 'work'::listing_kind, 'offer', 'offer-digital',
                  null, 'final_accepted'::order_status, 'final_accepted', 'acct-buyer', 'acct-seller', 'acct-buyer', null,
                  'money'::settlement_type, 100, null, null, null,
                  '{"preview":"LIC-****-001"}'::jsonb, '{}'::jsonb,
                  '{"buyerAccountId":"acct-buyer","sellerAccountId":"acct-seller","fulfillerAccountId":"acct-seller"}'::jsonb,
                  now(), now()
                )
                """);
        String payload = "LIC-SECRET-001";
        jdbcTemplate.update("""
                insert into digital_inventory_items (
                  id, listing_id, encrypted_payload, payload_preview, payload_hash, status,
                  reserved_order_id, delivered_order_id, created_by_account_id, created_at, updated_at
                ) values (
                  'dinv-1', 'listing-digital', ?, 'LIC-****-001', ?, 'delivered',
                  'order-digital', 'order-digital', 'acct-seller', now(), now()
                )
                """, crypto.encrypt(payload), crypto.hash(payload));
        jdbcTemplate.update("""
                insert into delivery_attempts (
                  id, order_id, provider, provider_order_id, status, idempotency_key, request_payload, receipt_payload, error_message, created_at, updated_at
                ) values (
                  'ddel-1', 'order-digital', 'digital_inventory', 'dinv-1', 'succeeded', 'digital:order-digital',
                  '{"inventoryItemId":"dinv-1"}'::jsonb,
                  '{"preview":"LIC-****-001","inventoryItemId":"dinv-1","digitalDeliveryStatus":"delivered","deliveredAt":"2026-05-20T00:00:00Z"}'::jsonb,
                  null, now(), now()
                )
                """);
    }

    @Test
    void buyerCanRevealDeliveredDigitalPayload() throws Exception {
        mockMvc.perform(get("/api/v1/orders/MF260512ORD000001X/digital-delivery")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value("MF260512ORD000001X"))
                .andExpect(jsonPath("$.inventoryItemId").value("dinv-1"))
                .andExpect(jsonPath("$.payload").value("LIC-SECRET-001"))
                .andExpect(jsonPath("$.payloadPreview").value("LIC-****-001"));
    }

    @Test
    void nonParticipantCannotRevealDeliveredDigitalPayload() throws Exception {
        mockMvc.perform(get("/api/v1/orders/MF260512ORD000001X/digital-delivery")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-stranger")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sellerCannotRevealDeliveredDigitalPayload() throws Exception {
        mockMvc.perform(get("/api/v1/orders/MF260512ORD000001X/digital-delivery")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller")))
                .andExpect(status().isForbidden());
    }
}
