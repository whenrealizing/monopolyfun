package com.monopolyfun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OrderFirstApiSmokeTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedReadModelFixture() {
        jdbcTemplate.execute("truncate table business_id_sequences, share_release_requests, project_roles, organization_events, offers, requests, projects, order_events, shares_ledger, proofs, orders, listings, markets, accounts cascade");

        insertAccount("acct-lead", "@founder", "Founder Lead", "{\"agentSummary\":\"Human operator\"}");
        insertAccount("acct-risk-agent", "@risk_worker", "Risk Worker Account", "{\"agentSummary\":\"Worker agent\"}");
        insertAccount("acct-growth-agent", "@growth_worker", "Growth Worker Account", "{\"agentSummary\":\"Growth agent\"}");
        insertRootProject();

        jdbcTemplate.update("""
                        insert into markets (
                          id, name, summary, listing_goal, lead_account_id, source_ref, surface_url,
                          settlement_type, next_curve_slot, status, lead_last_active_at, lead_seat_status, metadata, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?::settlement_type, ?, ?::market_status, ?, ?, '{}'::jsonb, ?, ?)
                        """,
                "mkt-opencompany-ai-finance",
                "AI 财务分析公司",
                "围绕公开市场、社媒风险、链上安全事件，持续交付可验证的情报页面和报告流。",
                "完成 3 个可公开展示的分析页面，达到持续招募和上市标准。",
                "acct-lead",
                "github.com/monopolyfun/ai-finance-lab",
                "https://opencompany.example/ai-finance",
                "shares",
                7,
                "active",
                timestamp("2026-05-04T10:00:00Z"),
                "occupied",
                timestamp("2026-05-01T10:00:00Z"),
                timestamp("2026-05-04T10:00:00Z"));

        insertListing("listing-risk-radar", "mkt-opencompany-ai-finance", "work", null,
                "做推特风险新闻抓取与日报页面", "quest", "quest/risk-radar",
                "抓取 20 条公开风险新闻，聚合为可访问日报页面，并标注来源、时间和风险类型。",
                "必须提交 repo diff、preview URL、样例数据截图；summary 说明抓取范围和失败源。",
                "accepted work order -> mint shares by curve slot", 3, 1, 3, "shares", "open", "acct-lead",
                "2026-05-03T08:00:00Z", "2026-05-04T10:00:00Z");
        insertListing("listing-report-page", "mkt-opencompany-ai-finance", "work", null,
                "设计每日报告公开页面", "quest", "quest/public-report",
                "把风险新闻、链上事件、人工 review 汇总成一个公开报告页面。",
                "提交 preview URL、组件截图、关键数据字段说明。",
                "预计奖励 shares。", 2, 0, 2, "shares", "open", "acct-lead",
                "2026-05-03T10:20:00Z", "2026-05-04T10:00:00Z");
        insertProject("project-opencompany-ai-finance", "acct-lead", "AI 财务分析公司",
                "围绕公开市场、社媒风险、链上安全事件，持续交付可验证的情报页面和报告流。",
                "持续交付可验证情报页面和报告流。", "limited", 3, 1, "active",
                "2026-05-03T07:00:00Z", "2026-05-04T10:00:00Z");
        insertOffer("offer-risk-radar", "acct-lead", "做推特风险新闻抓取与日报页面",
                "抓取 20 条公开风险新闻，聚合为可访问日报页面，并标注来源、时间和风险类型。",
                "必须提交 repo diff、preview URL、样例数据截图；summary 说明抓取范围和失败源。",
                871, "SHARES", "internal", "market", "monopolyfun", "SHARE", "limited", 3, 1, "open",
                "2026-05-03T08:00:00Z", "2026-05-04T10:00:00Z");

        insertOrderWithoutProof("order-risk-radar", "mkt-opencompany-ai-finance", "listing-risk-radar", "work", "offer",
                "offer-risk-radar", null, "delivered", "waiting_lead_acceptance", "acct-risk-agent", "acct-risk-agent", null, "shares", 871, null, null,
                null, "{\"deliverableSpec\":\"risk radar preview\"}", "{\"curveSlot\":7}", "{}",
                "2026-05-03T08:10:00Z", "2026-05-04T08:20:00Z");
        insertProof("proof-risk-radar", "order-risk-radar", "work_proof", null, "acct-risk-agent",
                "已完成公开风险新闻抓取 MVP，包含日报页面。",
                "[{\"label\":\"Preview\",\"href\":\"https://preview.example/risk-radar\"}]",
                "[]", "{\"commitSha\":\"risk-radar-001\"}", "agent", "agent-session-001", "codex-runtime", null,
                "2026-05-04T08:20:00Z");
        jdbcTemplate.update("update orders set proof_id = ? where id = ?", "proof-risk-radar", "order-risk-radar");
        jdbcTemplate.update("""
                        insert into order_events (id, order_id, event_type, actor_account_id, payload, created_at)
                        values (?, ?, ?, ?, ?::jsonb, ?)
                        """, "evt-001", "order-risk-radar", "proof_submitted", "acct-risk-agent",
                "{\"label\":\"Proof submitted with preview and repo diff\"}", timestamp("2026-05-04T08:20:00Z"));
    }

    @Test
    void exposesPostAndOrderReadModels() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].projectNo").value(fixtureNo("PRJ", "project-opencompany-ai-finance")))
                .andExpect(jsonPath("$.pageInfo.hasMore").value(false));

        mockMvc.perform(get("/api/v1/offers/" + fixtureNo("OFF", "offer-risk-radar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.stockTotal").value(3));

        mockMvc.perform(get("/api/v1/orders/" + fixtureNo("ORD", "order-risk-radar")).with(SecurityTestSupport.session(jdbcTemplate, "acct-lead")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("delivered"))
                .andExpect(jsonPath("$.post.kind").value("offer"))
                .andExpect(jsonPath("$.post.id").value("offer-risk-radar"))
                .andExpect(jsonPath("$.availableActions[0].id").value("accept_order"));
    }

    @Test
    void blocksDirectClaimEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/listings/listing-report-page/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-growth-agent"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-growth-agent"
                                }
                                """))
                .andExpect(status().isNotFound());

        Integer activeOrdersCount = jdbcTemplate.queryForObject(
                "select active_orders_count from listings where id = ?",
                Integer.class,
                "listing-report-page");
        Integer nextCurveSlot = jdbcTemplate.queryForObject(
                "select next_curve_slot from markets where id = ?",
                Integer.class,
                "mkt-opencompany-ai-finance");
        org.junit.jupiter.api.Assertions.assertEquals(0, activeOrdersCount);
        org.junit.jupiter.api.Assertions.assertEquals(7, nextCurveSlot);
    }

    @Test
    void rejectsWriteWhenHeaderAccountDoesNotMatchActor() throws Exception {
        mockMvc.perform(post("/api/v1/work/orders/" + fixtureNo("ORD", "order-risk-radar") + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-risk-agent"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acceptedByAccountId": "acct-lead",
                                  "note": "header mismatch"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Request actor must match authenticated account"));
    }

    private void insertAccount(String id, String handle, String displayName, String metadata) {
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values (?, ?, ?, ?::jsonb)
                """, id, handle, displayName, metadata);
    }

    private void insertListing(
            String id,
            String marketId,
            String kind,
            String parentOrderId,
            String title,
            String subjectType,
            String subjectRef,
            String deliverableSpec,
            String proofSpec,
            String settlementSpec,
            int inventoryLimit,
            int activeOrdersCount,
            int stockTotal,
            String settlementType,
            String status,
            String openedByAccountId,
            String createdAt,
            String updatedAt) {
        jdbcTemplate.update("""
                        insert into listings (
                          id, market_id, kind, parent_order_id, title, subject_type, subject_ref, deliverable_spec,
                          proof_spec, settlement_spec, inventory_limit, active_orders_count, stock_total,
                          settlement_type, status, opened_by_account_id, metadata, created_at, updated_at
                        ) values (?, ?, ?::listing_kind, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::settlement_type, ?::listing_status, ?, '{}'::jsonb, ?, ?)
                        """, id, marketId, kind, parentOrderId, title, subjectType, subjectRef, deliverableSpec, proofSpec,
                settlementSpec, inventoryLimit, activeOrdersCount, stockTotal, settlementType, status, openedByAccountId,
                timestamp(createdAt), timestamp(updatedAt));
    }

    private void insertOrderWithoutProof(
            String id,
            String marketId,
            String listingId,
            String kind,
            String postKind,
            String postId,
            String parentOrderId,
            String status,
            String displayPhase,
            String claimedByAccountId,
            String submittedByAccountId,
            String acceptedByAccountId,
            String settlementType,
            Integer settlementAmount,
            String closedReason,
            String disputeReason,
            String reviewListingId,
            String deliverySnapshot,
            String settlementSnapshot,
            String metadata,
            String createdAt,
            String updatedAt) {
        jdbcTemplate.update("""
                        insert into orders (
                          id, order_no, market_id, listing_id, kind, post_kind, post_id, parent_order_id, status, display_phase, claimed_by_account_id,
                          submitted_by_account_id, accepted_by_account_id, proof_id, settlement_type, settlement_amount,
                          closed_reason, dispute_reason, review_listing_id, delivery_snapshot, settlement_snapshot, metadata,
                          created_at, updated_at
                        ) values (?, ?, ?, ?, ?::listing_kind, ?, ?, ?, ?::order_status, ?, ?, ?, ?, null, ?::settlement_type, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                        """, id, fixtureNo("ORD", id), marketId, listingId, kind, postKind, postId, parentOrderId, status, displayPhase,
                claimedByAccountId, submittedByAccountId, acceptedByAccountId, settlementType, settlementAmount,
                closedReason, disputeReason, reviewListingId, deliverySnapshot, settlementSnapshot, metadata,
                timestamp(createdAt), timestamp(updatedAt));
    }

    private void insertProof(
            String id,
            String orderId,
            String kind,
            String parentOrderId,
            String submittedByAccountId,
            String summary,
            String links,
            String artifacts,
            String proofPayload,
            String executionMode,
            String agentSessionId,
            String agentRuntime,
            String decision,
            String createdAt) {
        jdbcTemplate.update("""
                        insert into proofs (
                          id, order_id, kind, parent_order_id, submitted_by_account_id, summary, links, artifacts,
                          proof_payload, execution_mode, agent_session_id, agent_runtime, decision, created_at
                        ) values (?, ?, ?::proof_kind, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::execution_mode, ?, ?, ?::review_decision, ?)
                        """, id, orderId, kind, parentOrderId, submittedByAccountId, summary, links, artifacts, proofPayload,
                executionMode, agentSessionId, agentRuntime, decision, timestamp(createdAt));
    }

    private Timestamp timestamp(String value) {
        return Timestamp.from(Instant.parse(value));
    }

    private void insertOffer(
            String id,
            String actorAccountId,
            String title,
            String description,
            String deliveryStandard,
            Integer priceAmount,
            String currency,
            String paymentMethod,
            String paymentProfile,
            String paymentNetwork,
            String paymentAsset,
            String inventoryPolicy,
            int stockTotal,
            int stockSold,
            String status,
            String createdAt,
            String updatedAt) {
        jdbcTemplate.update("""
                        insert into offers (
                          id, offer_no, actor_account_id, title, description, delivery_standard, price_amount, currency,
                          payment_method, payment_profile, payment_network, payment_asset, inventory_policy,
                          stock_total, stock_sold, status, metadata, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?)
                        """, id, fixtureNo("OFF", id), actorAccountId, title, description, deliveryStandard, priceAmount, currency, paymentMethod,
                paymentProfile, paymentNetwork, paymentAsset, inventoryPolicy, stockTotal, stockSold, status,
                timestamp(createdAt), timestamp(updatedAt));
    }

    private void insertProject(
            String id,
            String ownerAccountId,
            String title,
            String summary,
            String oneSentence,
            String inventoryPolicy,
            int stockTotal,
            int stockSold,
            String status,
            String createdAt,
            String updatedAt) {
        jdbcTemplate.update("""
                        insert into projects (
                          id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence, inventory_policy,
                          stock_total, stock_sold, status, metadata, created_at, updated_at
                        ) values (?, ?, ?, 'child', 'project-root', ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, ?, ?)
                        """, id, fixtureNo("PRJ", id), ownerAccountId, title, summary, oneSentence, inventoryPolicy, stockTotal, stockSold, status,
                timestamp(createdAt), timestamp(updatedAt));
    }

    private void insertRootProject() {
        jdbcTemplate.update("""
                insert into projects (
                  id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence,
                  inventory_policy, stock_total, stock_sold, status, metadata, created_at, updated_at
                ) values (
                  'project-root', 'ROOT', 'acct-lead', 'root', null, 'Root Project',
                  'System operations, governance, and root shares settlement.',
                  'System operations, governance, and root shares settlement.',
                  'unlimited', null, 0, 'active', '{}'::jsonb, ?, ?
                )
                """, timestamp("2026-05-01T00:00:00Z"), timestamp("2026-05-01T00:00:00Z"));
    }

    private String fixtureNo(String code, String id) {
        return "MF260505" + code + "%06dX".formatted(Math.floorMod(id.hashCode(), 1_000_000));
    }
}
