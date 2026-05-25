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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WorkThreadBountyApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("""
                truncate table distribution_claims, distribution_batches, project_revenue_addresses,
                  contribution_ledger, work_thread_reviews, work_results, work_threads,
                  shares_ledger, project_share_pools, projects, accounts cascade
                """);
        insertAccount("acct-owner", "@owner", "Owner");
        insertAccount("acct-dev", "@dev", "Dev");
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
                .andExpect(jsonPath("$.proof.length()").value(2));
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
