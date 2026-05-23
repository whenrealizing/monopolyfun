package com.monopolyfun;

import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PublicProfileApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RootProjectService rootProjectService;

    @Autowired
    private ProjectRoleRepository projectRoleRepository;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("""
                truncate table business_id_sequences, share_release_requests,
                project_roles, organization_events, order_progress_updates, offers, requests, projects,
                order_events, shares_ledger, proofs, orders, listings, markets, identity_badges,
                identity_facts, spring_session_attributes, spring_session, accounts cascade
                """);

        insertAccount("acct-root", "@root", "Root Owner");
        insertAccount("acct-lead", "@lead", "Lead Builder");
        insertAccount("acct-other", "@other", "Other Builder");

        rootProjectService.ensureRootProject("acct-root");
        projectRoleRepository.assignRole(RootProjectService.ROOT_PROJECT_ID, ProjectRoleCode.SYSTEM_CTO, "acct-lead", "acct-root");

        insertIdentityFact(
                "ifact-github-lead",
                "acct-lead",
                "github_oauth",
                "github",
                "external_identity",
                "verified",
                "octolead",
                """
                        {
                          "handle": "octolead",
                          "displayName": "Octo Lead",
                          "avatarUrl": "https://example.com/octolead.png",
                          "profileUrl": "https://github.com/octolead"
                        }
                        """);
        insertIdentityBadge("ibadge-lead", "acct-lead", "identity", "trusted_builder", "Trusted Builder", "badge-check", 88);

        insertOffer("offer-public", "acct-lead", "公开 Offer", "market_public");
        insertOffer("offer-private", "acct-lead", "私有 Offer", "participant_only");
        insertRequest("request-public", "acct-lead", "公开 Request", "market_public");
        insertRequest("request-private", "acct-lead", "私有 Request", "participant_only");
        insertProject("project-public", "acct-lead", "公开 Project", "market_public");
        insertProject("project-private", "acct-lead", "私有 Project", "participant_only");
    }

    @Test
    void getPublicProfileReturnsPublicIdentityAndRecentPublicActivity() throws Exception {
        mockMvc.perform(get("/api/v1/public/profiles/lead"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.account.id").value("lead"))
                .andExpect(jsonPath("$.profile.account.handle").value("@lead"))
                .andExpect(jsonPath("$.profile.account.displayName").value("Lead Builder"))
                .andExpect(jsonPath("$.profile.verified").value(true))
                .andExpect(jsonPath("$.profile.verifiedFactCount").value(1))
                .andExpect(jsonPath("$.profile.badges[?(@.code == 'trusted_builder')]").exists())
                .andExpect(jsonPath("$.profile.badges[?(@.code == 'system_cto')]").exists())
                .andExpect(jsonPath("$.profile.linkedAccounts[0].handle").value("octolead"))
                .andExpect(jsonPath("$.profile.displaySkin.displayHandle").value("lead"))
                .andExpect(jsonPath("$.activity.offers.length()").value(1))
                .andExpect(jsonPath("$.activity.offers[0].title").value("公开 Offer"))
                .andExpect(jsonPath("$.activity.requests.length()").value(1))
                .andExpect(jsonPath("$.activity.requests[0].title").value("公开 Request"))
                .andExpect(jsonPath("$.activity.projects.length()").value(1))
                .andExpect(jsonPath("$.activity.projects[0].title").value("公开 Project"));
    }

    @Test
    void getPublicProfileAcceptsAtPrefixedHandle() throws Exception {
        mockMvc.perform(get("/api/v1/public/profiles/{handle}", "@lead"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.account.id").value("lead"));
    }

    @Test
    void getPublicProfileReturnsNotFoundForUnknownHandle() throws Exception {
        mockMvc.perform(get("/api/v1/public/profiles/missing"))
                .andExpect(status().isNotFound());
    }

    private void insertAccount(String id, String handle, String displayName) {
        jdbcTemplate.update("""
                        insert into accounts (id, handle, display_name, password_hash, metadata, created_at, updated_at)
                        values (?, ?, ?, null, '{}'::jsonb, ?, ?)
                        """,
                id,
                handle,
                displayName,
                timestamp("2026-05-01T00:00:00Z"),
                timestamp("2026-05-01T00:00:00Z"));
    }

    private void insertIdentityFact(
            String id,
            String accountId,
            String certifierId,
            String provider,
            String factType,
            String status,
            String platformUserId,
            String payloadJson) {
        jdbcTemplate.update("""
                        insert into identity_facts (
                          id, account_id, challenge_id, certifier_id, provider, fact_type, verification_method,
                          status, platform_user_id, payload, verified_at, expires_at, revoked_at, created_at, updated_at
                        ) values (?, ?, null, ?, ?, ?, 'oauth', ?, ?, ?::jsonb, ?, null, null, ?, ?)
                        """,
                id,
                accountId,
                certifierId,
                provider,
                factType,
                status,
                platformUserId,
                payloadJson,
                timestamp("2026-05-02T00:00:00Z"),
                timestamp("2026-05-02T00:00:00Z"),
                timestamp("2026-05-02T00:00:00Z"));
    }

    private void insertIdentityBadge(
            String id,
            String accountId,
            String kind,
            String code,
            String label,
            String icon,
            int weight) {
        jdbcTemplate.update("""
                        insert into identity_badges (
                          id, account_id, kind, code, label, icon, source_certifier_id, source_fact_id, weight, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, null, null, ?, ?, ?)
                        """,
                id,
                accountId,
                kind,
                code,
                label,
                icon,
                weight,
                timestamp("2026-05-02T00:00:00Z"),
                timestamp("2026-05-02T00:00:00Z"));
    }

    private void insertOffer(String id, String actorAccountId, String title, String visibility) {
        jdbcTemplate.update("""
                        insert into offers (
                          id, offer_no, actor_account_id, title, description, delivery_standard, price_amount, currency,
                          payment_method, payment_profile, payment_network, payment_asset, inventory_policy,
                          stock_total, stock_sold, status, metadata, created_at, updated_at
                        ) values (?, ?, ?, ?, 'desc', 'standard', 100, 'USD', 'okx_direct_pay', '', '', 'USDC',
                          'limited', 1, 0, 'open', ?::jsonb, ?, ?)
                        """,
                id,
                fixtureNo("OFF", id),
                actorAccountId,
                title,
                """
                        {"visibility":"%s","tradeStatus":"open"}
                        """.formatted(visibility),
                timestamp("2026-05-03T00:00:00Z"),
                timestamp("2026-05-03T00:00:00Z"));
    }

    private void insertRequest(String id, String actorAccountId, String title, String visibility) {
        jdbcTemplate.update("""
                        insert into requests (
                          id, request_no, actor_account_id, title, description, delivery_standard, budget_amount, currency,
                          payment_method, payment_profile, payment_network, payment_asset, inventory_policy,
                          stock_total, stock_filled, status, deadline_at, metadata, created_at, updated_at
                        ) values (?, ?, ?, ?, 'desc', 'standard', 100, 'USD', 'okx_direct_pay', '', '', 'USDC',
                          'limited', 1, 0, 'open', null, ?::jsonb, ?, ?)
                        """,
                id,
                fixtureNo("REQ", id),
                actorAccountId,
                title,
                """
                        {"visibility":"%s","tradeStatus":"open"}
                        """.formatted(visibility),
                timestamp("2026-05-03T00:00:00Z"),
                timestamp("2026-05-03T00:00:00Z"));
    }

    private void insertProject(String id, String ownerAccountId, String title, String visibility) {
        jdbcTemplate.update("""
                        insert into projects (
                          id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence,
                          inventory_policy, stock_total, stock_sold, status, metadata, created_at, updated_at
                        ) values (?, ?, ?, 'child', 'project-root', ?, 'summary', 'one sentence', 'limited', 3, 1, 'active', ?::jsonb, ?, ?)
                        """,
                id,
                fixtureNo("PRJ", id),
                ownerAccountId,
                title,
                """
                        {"visibility":"%s","tradeStatus":"open","description":"public project"}
                        """.formatted(visibility),
                timestamp("2026-05-04T00:00:00Z"),
                timestamp("2026-05-04T00:00:00Z"));
    }

    private String fixtureNo(String code, String id) {
        return "MF260507" + code + "%06dX".formatted(Math.floorMod(id.hashCode(), 1_000_000));
    }

    private Timestamp timestamp(String value) {
        return Timestamp.from(Instant.parse(value));
    }
}
