package com.monopolyfun;

import com.monopolyfun.modules.workthread.infra.chain.DistributionChainReceiptVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "monopolyfun.revenue.chain-id=eip155:31337",
        "monopolyfun.revenue.chain-name=BSC",
        "monopolyfun.revenue.asset=BNB",
        "monopolyfun.revenue.token-address=0x8888888888888888888888888888888888888888",
        "monopolyfun.revenue.router-address=0x9999999999999999999999999999999999999999",
        "monopolyfun.revenue.min-distribution-minor=100000",
        "monopolyfun.revenue.minor-per-share=20"
})
@AutoConfigureMockMvc
@Testcontainers
class WorkThreadBountyApiTest extends AbstractPostgresIntegrationTest {
    private static final String CLAIM_TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_TX_HASH = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private DistributionChainReceiptVerifier distributionChainReceiptVerifier;

    @BeforeEach
    void resetSchema() {
        org.mockito.Mockito.reset(distributionChainReceiptVerifier);
        jdbcTemplate.execute("""
                truncate table distribution_claims, distribution_entitlements, distribution_batches, project_revenue_addresses,
                  contribution_ledger, work_thread_reviews, work_results, work_threads,
                  shares_ledger, project_share_pools, projects, accounts cascade
                """);
        insertAccount("acct-owner", "@owner", "Owner");
        insertAccount("acct-dev", "@dev", "Dev");
        insertAccount("acct-dev2", "@dev2", "Dev Two");
        insertAccount("acct-late", "@late", "Late Dev");
        insertRootProject();
        insertProject();
    }

    @Test
    void openclawBountyFlowCreatesContributionAndClaimProof() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/projects/proj-1/work-threads")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-owner",
                                  "title": "Fix login error copy",
                                  "goal": "Make auth failures clear",
                                  "deliverables": ["PR link", "Test summary", "Changed files"],
                                  "acceptanceCriteria": ["Specific error reason", "Auth error test passes"],
                                  "taskValue": 5000,
                                  "bountyAmountMinor": 8000,
                                  "bountyToken": "USDC",
                                  "repoRef": "github.com/org/app",
                                  "issueUrl": "https://github.com/org/app/issues/1842"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andReturn().getResponse().getContentAsString();
        String threadId = JsonTestValue.extract(createResponse, "id");

        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","runtime":"openclaw"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","runtime":"openclaw"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"));

        mockMvc.perform(get("/api/v1/work-threads/" + threadId + "/packet")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskValue").value(5000))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("workThreadId: " + threadId)));

        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/result")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-dev",
                                  "resultMarkdown": "---\\npacketType: work_result\\nworkThreadId: %s\\n---\\n# Result\\n\\n## Summary\\nFixed login error copy.\\n\\n## Evidence\\n- PR: https://github.com/org/app/pull/1842\\n- Test: pnpm test passed\\n\\n## Changed Files\\n- apps/web/login/page.tsx\\n- tests/login.spec.ts\\n",
                                  "runtime": "openclaw"
                                }
                                """.formatted(threadId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.prUrl").value("https://github.com/org/app/pull/1842"));

        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerAccountId":"acct-owner","decision":"accept","reason":"PR matches acceptance criteria"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("settled"));

        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerAccountId":"acct-owner","decision":"reject","reason":"Late stale review"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/projects/proj-1/workroom")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workThreads[0].latestResult.status").value("accepted"));

        mockMvc.perform(post("/api/v1/projects/proj-1/revenue-address")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","chainId":"eip155:31337","contractAddress":"0x9999999999999999999999999999999999999999","tokenAddress":"0x8888888888888888888888888888888888888888"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","period":"2026-05","totalRevenueMinor":100000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSnapshotShares").value(5000));

        mockMvc.perform(get("/api/v1/projects/proj-1/workroom")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(false))
                .andExpect(jsonPath("$.workThreads[0].status").value("settled"))
                .andExpect(jsonPath("$.workThreads[0].latestResult.prUrl").value("https://github.com/org/app/pull/1842"))
                .andExpect(jsonPath("$.contributors[0].accountId").value("acct-dev"))
                .andExpect(jsonPath("$.contributors[0].totalShares").value(5000))
                .andExpect(jsonPath("$.distributions[0].myClaimableAmountMinor").value(100000));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","period":"2026-05","totalRevenueMinor":100000}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-05/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","walletAddress":"0x1111111111111111111111111111111111111111"}
	                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(100000))
                .andExpect(jsonPath("$.walletAddress").value("0x1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.proof.length()").value(0));

        mockMvc.perform(get("/api/v1/projects/proj-1/workroom")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRewards.claimableAmountMinor").value(0))
                .andExpect(jsonPath("$.distributions[0].myClaimableAmountMinor").value(0));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-05/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","walletAddress":"0x1111111111111111111111111111111111111111"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletAddress").value("0x1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.status").value("claimable"));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-05/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","walletAddress":"0x2222222222222222222222222222222222222222"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-05/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","txHash":"%s"}
                                """.formatted(CLAIM_TX_HASH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletAddress").value("0x1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.txHash").value(CLAIM_TX_HASH))
                .andExpect(jsonPath("$.status").value("submitted"));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-05/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","txHash":"%s","txConfirmed":true}
                                """.formatted(CLAIM_TX_HASH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletAddress").value("0x1111111111111111111111111111111111111111"))
                .andExpect(jsonPath("$.txHash").value(CLAIM_TX_HASH))
                .andExpect(jsonPath("$.status").value("claimed"));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-05/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","walletAddress":"0x1111111111111111111111111111111111111111","txHash":"%s"}
                                """.formatted(OTHER_TX_HASH)))
                .andExpect(status().isConflict());

        String lateThreadId = createThread("Add late metrics", 5000);
        claimThread(lateThreadId, "acct-late");
        submitResult(lateThreadId, "acct-late", "1843");
        reviewThread(lateThreadId, "accept");

        mockMvc.perform(get("/api/v1/projects/proj-1/workroom")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-late")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRewards.totalShares").value(4800))
                .andExpect(jsonPath("$.distributions[0].myClaimableAmountMinor").value(0));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-05/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-late"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-late","walletAddress":"0x3333333333333333333333333333333333333333"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void autoPricesWorkThreadAndInitializesRevenueDistribution() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/projects/proj-1/work-threads")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-owner",
                                  "title": "Run revenue claim end to end",
                                  "goal": "Complete a hard creative claim flow",
                                  "deliverables": ["Report", "Balance evidence"],
                                  "acceptanceCriteria": ["Claim can be verified"],
                                  "taskValue": 0,
                                  "difficulty": "hard",
                                  "creativity": "creative",
                                  "bountyAmountMinor": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskValue").value(6250))
                .andReturn().getResponse().getContentAsString();
        String threadId = JsonTestValue.extract(createResponse, "id");

        claimThread(threadId, "acct-dev");
        submitResult(threadId, "acct-dev", "3001");
        reviewThread(threadId, "accept");

        mockMvc.perform(get("/api/v1/projects/proj-1/workroom")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueAutomation.configured").value(true))
                .andExpect(jsonPath("$.revenueAutomation.chainId").value("eip155:31337"))
                .andExpect(jsonPath("$.revenueAutomation.nextDistributionRevenueMinor").value(125000));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","period":"2026-08"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenueMinor").value(125000))
                .andExpect(jsonPath("$.totalSnapshotShares").value(6250));

        mockMvc.perform(get("/api/v1/projects/proj-1/workroom")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueAddress.chainId").value("eip155:31337"))
                .andExpect(jsonPath("$.revenueAddress.contractAddress").value("0x9999999999999999999999999999999999999999"));
    }

    private String createThread(String title, int taskValue) throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/projects/proj-1/work-threads")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-owner",
                                  "title": "%s",
                                  "goal": "Ship %s",
                                  "deliverables": ["PR link", "Test summary", "Changed files"],
                                  "acceptanceCriteria": ["PR ready for review", "Tests pass"],
                                  "taskValue": %d,
                                  "bountyAmountMinor": 0,
                                  "bountyToken": "USDC"
                                }
                                """.formatted(title, title, taskValue)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonTestValue.extract(createResponse, "id");
    }

    private void claimThread(String threadId, String accountId) throws Exception {
        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"%s","runtime":"openclaw"}
                                """.formatted(accountId)))
                .andExpect(status().isOk());
    }

    private void submitResult(String threadId, String accountId, String prNumber) throws Exception {
        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/result")
                        .with(SecurityTestSupport.session(jdbcTemplate, accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "%s",
                                  "resultMarkdown": "---\\npacketType: work_result\\nworkThreadId: %s\\n---\\n# Result\\n\\n## Summary\\nImplemented requested change.\\n\\n## Evidence\\n- PR: https://github.com/org/app/pull/%s\\n- Test: pnpm test passed\\n\\n## Changed Files\\n- apps/web/page.tsx\\n",
                                  "runtime": "openclaw"
                                }
                                """.formatted(accountId, threadId, prNumber)))
                .andExpect(status().isOk());
    }

    private void reviewThread(String threadId, String decision) throws Exception {
        mockMvc.perform(post("/api/v1/work-threads/" + threadId + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerAccountId":"acct-owner","decision":"%s","reason":"Checked"}
                """.formatted(decision)))
                .andExpect(status().isOk());
    }

    private void upsertRevenueAddress(String chainId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/proj-1/revenue-address")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","chainId":"%s","contractAddress":"0x9999999999999999999999999999999999999999","tokenAddress":"0x8888888888888888888888888888888888888888"}
                                """.formatted(chainId)))
                .andExpect(status().isOk());
    }

    @Test
    void distributionSnapshotsSplitMultipleDevelopersAndRejectUnverifiedConfirmation() throws Exception {
        String firstThreadId = createThread("Ship revenue wallet", 5000);
        claimThread(firstThreadId, "acct-dev");
        submitResult(firstThreadId, "acct-dev", "2001");
        reviewThread(firstThreadId, "accept");

        String secondThreadId = createThread("Ship claim status", 5000);
        claimThread(secondThreadId, "acct-dev2");
        submitResult(secondThreadId, "acct-dev2", "2002");
        reviewThread(secondThreadId, "accept");

        upsertRevenueAddress("eip155:56");
        mockMvc.perform(post("/api/v1/projects/proj-1/distributions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","period":"2026-06","totalRevenueMinor":98000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSnapshotShares").value(9800));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-06/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","walletAddress":"0x1111111111111111111111111111111111111111"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(50000))
                .andExpect(jsonPath("$.status").value("claimable"));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-06/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev2","walletAddress":"0x2222222222222222222222222222222222222222"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(48000))
                .andExpect(jsonPath("$.status").value("claimable"));

        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Revenue chain RPC is not configured for eip155:56"))
                .when(distributionChainReceiptVerifier).verifyClaim(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq(CLAIM_TX_HASH));

        // 中文注释：用户回填 txHash 只能进入 submitted；claimed 需要链上 verifier 明确放行。
        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-06/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","txHash":"%s"}
                                """.formatted(CLAIM_TX_HASH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-06/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-dev"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-dev","txHash":"%s","txConfirmed":true}
                                """.formatted(CLAIM_TX_HASH)))
                .andExpect(status().isConflict());

        String lateThreadId = createThread("Late period work", 5000);
        claimThread(lateThreadId, "acct-late");
        submitResult(lateThreadId, "acct-late", "2003");
        reviewThread(lateThreadId, "accept");

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-owner","period":"2026-07","totalRevenueMinor":144000}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSnapshotShares").value(14405));

        mockMvc.perform(post("/api/v1/projects/proj-1/distributions/2026-07/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-late"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId":"acct-late","walletAddress":"0x3333333333333333333333333333333333333333"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(46034));
    }

    @Test
    void rejectsInvalidBountyAmount() throws Exception {
        mockMvc.perform(post("/api/v1/projects/proj-1/work-threads")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-owner",
                                  "title": "Invalid bounty",
                                  "goal": "Reject negative bounty",
                                  "deliverables": ["PR link"],
                                  "acceptanceCriteria": ["Tests pass"],
                                  "taskValue": 1000,
                                  "bountyAmountMinor": -1
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private void insertAccount(String id, String handle, String displayName) {
        jdbcTemplate.update("""
                        insert into accounts (id, handle, display_name, password_hash, metadata, created_at, updated_at)
                        values (?, ?, ?, null, '{}'::jsonb, ?, ?)
                        """,
                id, handle, displayName, timestamp("2026-05-25T00:00:00Z"), timestamp("2026-05-25T00:00:00Z"));
    }

    private void insertRootProject() {
        jdbcTemplate.update("""
                        insert into projects (id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence,
                          inventory_policy, stock_total, stock_sold, status, metadata, created_at, updated_at)
                        values ('root-project', 'ROOT', 'acct-owner', 'root', null, 'Root', 'Root', 'Root',
                          'unlimited', null, 0, 'active', '{}'::jsonb, ?, ?)
                        """, timestamp("2026-05-25T00:00:00Z"), timestamp("2026-05-25T00:00:00Z"));
    }

    private void insertProject() {
        jdbcTemplate.update("""
                        insert into projects (id, project_no, owner_account_id, project_level, parent_project_id, title, summary, one_sentence,
                          inventory_policy, stock_total, stock_sold, status, metadata, created_at, updated_at)
                        values ('proj-1', 'MF260525PRJ000001X', 'acct-owner', 'child', 'root-project', 'App', 'App', 'Fix app',
                          'unlimited', null, 0, 'active', '{}'::jsonb, ?, ?)
                        """, timestamp("2026-05-25T00:00:00Z"), timestamp("2026-05-25T00:00:00Z"));
    }

    private Timestamp timestamp(String value) {
        return Timestamp.from(Instant.parse(value));
    }

    private static final class JsonTestValue {
        private static String extract(String json, String field) {
            String marker = "\"" + field + "\":\"";
            int start = json.indexOf(marker);
            if (start < 0) {
                throw new IllegalArgumentException("Missing field " + field + " in " + json);
            }
            int valueStart = start + marker.length();
            int valueEnd = json.indexOf('"', valueStart);
            return json.substring(valueStart, valueEnd);
        }
    }
}
