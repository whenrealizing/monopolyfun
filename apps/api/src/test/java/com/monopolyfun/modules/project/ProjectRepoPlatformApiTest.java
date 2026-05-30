package com.monopolyfun;

import com.monopolyfun.modules.repo.infra.RepoProviderClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectRepoPlatformApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RepoProviderClient repoProviderClient;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("truncate table project_external_refs, repo_jobs, work_trust_events, work_reviews, work_receipts, work_runs, work_items, business_id_sequences, share_release_requests, project_roles, organization_events, order_progress_updates, proof_assets, offers, requests, projects, order_events, shares_ledger, proofs, orders, listings, markets, accounts cascade");
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values
                ('acct-founder', '@founder', 'Founder', '{}'::jsonb),
                ('acct-worker', '@worker', 'Worker', '{}'::jsonb),
                ('acct-stranger', '@stranger', 'Stranger', '{}'::jsonb)
                """);
    }

    @Test
    void publishProjectWithoutReferenceLinksAutoProvisionsPlatformRepo() throws Exception {
        when(repoProviderClient.provisionPublicRepository(any())).thenReturn(new RepoProviderClient.ProvisionedRepository(
                "forgejo",
                "http://localhost:3001/monopolyfun-projects/mf-20260507-auto-research",
                "http://localhost:3001/monopolyfun-projects/mf-20260507-auto-research.git",
                "monopolyfun-projects",
                "mf-20260507-auto-research",
                "main",
                "public",
                Map.of("providerRepoId", "1001")));

        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "自动研究公司",
                                  "description": "自动建仓库并创建项目。",
                                  "goal": "持续维护自动研究仓库和交付任务。",
                                  "items": [
                                    {
                                      "name": "初始化任务",
                                      "deliveryStandard": "提交仓库初始化结论。",
                                      "acceptanceCriteria": ["提交仓库初始化结论"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectNo").isNotEmpty())
                .andExpect(jsonPath("$.project.referenceLinks[0]").value("http://localhost:3001/monopolyfun-projects/mf-20260507-auto-research"))
                .andExpect(jsonPath("$.project.repoProvider").value("forgejo"))
                .andExpect(jsonPath("$.project.repoOwner").value("monopolyfun-projects"))
                .andExpect(jsonPath("$.project.repoName").value("mf-20260507-auto-research"))
                .andExpect(jsonPath("$.project.maintenanceMode").value("repo_first"));
    }

    @Test
    void publishProjectUsesPlatformManagedRepoWithoutUserTemplateInput() throws Exception {
        when(repoProviderClient.provisionPublicRepository(any())).thenReturn(new RepoProviderClient.ProvisionedRepository(
                "forgejo",
                "http://localhost:3001/monopolyfun-projects/mf-20260510-template-project",
                "http://localhost:3001/monopolyfun-projects/mf-20260510-template-project.git",
                "monopolyfun-projects",
                "mf-20260510-template-project",
                "main",
                "public",
                Map.of("providerRepoId", "1003")));

        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "平台仓库公司",
                                  "description": "使用平台统一仓库策略创建项目。",
                                  "goal": "持续维护平台生成的仓库。",
                                  "items": [
                                    {
                                      "name": "初始化仓库任务",
                                      "deliveryStandard": "提交仓库初始化结论。",
                                      "acceptanceCriteria": ["提交仓库初始化结论"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.referenceLinks[0]").value("http://localhost:3001/monopolyfun-projects/mf-20260510-template-project"));
    }

    @Test
    void projectRepoDeliverySessionCanReportPullRequestAndFinalizeProof() throws Exception {
        when(repoProviderClient.provisionPublicRepository(any())).thenReturn(new RepoProviderClient.ProvisionedRepository(
                "forgejo",
                "http://localhost:3001/monopolyfun-projects/mf-20260507-code-task",
                "http://localhost:3001/monopolyfun-projects/mf-20260507-code-task.git",
                "monopolyfun-projects",
                "mf-20260507-code-task",
                "main",
                "public",
                Map.of("providerRepoId", "1002")));
        when(repoProviderClient.issueRepositoryAccess(any())).thenReturn(new RepoProviderClient.RepositoryAccess(
                "token-123",
                Instant.now().plusSeconds(3600),
                Map.of()));
        AtomicReference<String> expectedHeadBranch = new AtomicReference<>("");
        when(repoProviderClient.inspectPullRequest(any())).thenAnswer(invocation -> {
            RepoProviderClient.InspectPullRequestCommand command = invocation.getArgument(0);
            return new RepoProviderClient.PullRequestInspection(
                    "http://localhost:3001/monopolyfun-projects/mf-20260507-code-task",
                    command.prUrl(),
                    command.expectedHeadCommit(),
                    "main",
                    expectedHeadBranch.get(),
                    "open",
                    false,
                    false,
                    "not_required",
                    "NOT_REQUIRED",
                    "changed_files=1 additions=12 deletions=2",
                    Map.of());
        });

        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "代码交付公司",
                                  "description": "自动建仓库并推进代码 PR 交付。",
                                  "goal": "围绕公开仓库持续推进代码任务。",
                                  "items": [
                                    {
                                      "name": "交付首个代码 PR",
                                      "deliveryStandard": "提交真实 PR proof。",
                                      "acceptanceCriteria": ["提交真实 PR proof"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String projectNo = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/projectNo");
        String projectId = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");

        var workspace = mockMvc.perform(get("/api/v1/posts/" + projectNo + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);
        expectedHeadBranch.set("mf/%s-acct-worker".formatted(orderNo.toLowerCase()));

        var deliverySession = mockMvc.perform(post("/api/v1/work/repo-delivery-sessions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectNo": "%s",
                                  "orderNo": "%s",
                                  "runtime": "openclaw"
                                }
                                """.formatted(projectNo, orderNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectNo").value(projectNo))
                .andExpect(jsonPath("$.orderNo").value(orderNo))
                .andExpect(jsonPath("$.cloneUrl").doesNotExist())
                .andReturn();
        String sessionId = JsonTestSupport.readString(deliverySession.getResponse().getContentAsString(), "/deliverySessionId");

        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions/" + sessionId + "/report-pr")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prUrl": "http://localhost:3001/monopolyfun-projects/mf-20260507-code-task/pulls/3",
                                  "headCommit": "abc123def456",
                                  "diffSummary": "新增 repo delivery session 和 PR proof 绑定"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prUrl").value("http://localhost:3001/monopolyfun-projects/mf-20260507-code-task/pulls/3"))
                .andExpect(jsonPath("$.headCommit").value("abc123def456"))
                .andExpect(jsonPath("$.ciStatus").value("not_required"))
                .andExpect(jsonPath("$.status").value("pr_reported"));

        insertProofAsset(orderNo, "asset-proof-001");

        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions/" + sessionId + "/finalize-proof")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "OpenClaw 已完成代码修改并提交 PR。",
                                  "artifacts": ["asset://asset-proof-001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("delivered"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from work_receipts receipt
                        join work_runs run on run.id = receipt.work_run_id
                        join work_items item on item.id = run.work_item_id
                        where item.item_no = ?
                          and receipt.output -> 'prSecurity' ->> 'prUrl' = ?
                        """,
                Integer.class,
                "wb-delivery-result-" + orderNo,
                "http://localhost:3001/monopolyfun-projects/mf-20260507-code-task/pulls/3")).isEqualTo(1);
    }

    @Test
    void repoDeliveryEnforcesWorkerAccessAndOwnerRevisionLoop() throws Exception {
        when(repoProviderClient.provisionPublicRepository(any())).thenReturn(new RepoProviderClient.ProvisionedRepository(
                "forgejo",
                "http://localhost:3001/monopolyfun-projects/mf-20260510-review-loop",
                "http://localhost:3001/monopolyfun-projects/mf-20260510-review-loop.git",
                "monopolyfun-projects",
                "mf-20260510-review-loop",
                "main",
                "public",
                Map.of("providerRepoId", "1004")));
        when(repoProviderClient.issueRepositoryAccess(any())).thenReturn(new RepoProviderClient.RepositoryAccess(
                "token-worker",
                Instant.now().plusSeconds(3600),
                Map.of()));
        AtomicReference<String> expectedHeadBranch = new AtomicReference<>("");
        when(repoProviderClient.inspectPullRequest(any())).thenAnswer(invocation -> {
            RepoProviderClient.InspectPullRequestCommand command = invocation.getArgument(0);
            return new RepoProviderClient.PullRequestInspection(
                    "http://localhost:3001/monopolyfun-projects/mf-20260510-review-loop",
                    command.prUrl(),
                    command.expectedHeadCommit(),
                    "main",
                    expectedHeadBranch.get(),
                    "open",
                    false,
                    false,
                    "success",
                    "SUCCESS",
                    "changed_files=2 additions=20 deletions=1",
                    Map.of());
        });

        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "权限验收公司",
                                  "description": "验证普通执行方提交 PR 与 owner 返工验收。",
                                  "goal": "确保平台仓库交付权限和 owner 审核闭环可执行。",
                                  "items": [
                                    {
                                      "name": "提交权限测试 PR",
                                      "deliveryStandard": "提交 PR proof 并通过 owner 验收。",
                                      "acceptanceCriteria": ["PR CI 通过", "owner 可要求返工并再次验收"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectNo = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/projectNo");

        var workspace = mockMvc.perform(get("/api/v1/posts/" + projectNo + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String orderNo = orderNo(JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId"));
        expectedHeadBranch.set("mf/%s-acct-worker".formatted(orderNo.toLowerCase()));

        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-stranger"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectNo": "%s",
                                  "orderNo": "%s",
                                  "runtime": "openclaw"
                                }
                                """.formatted(projectNo, orderNo)))
                .andExpect(status().isForbidden());

        var deliverySession = mockMvc.perform(post("/api/v1/work/repo-delivery-sessions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectNo": "%s",
                                  "orderNo": "%s",
                                  "runtime": "openclaw"
                                }
                                """.formatted(projectNo, orderNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cloneUrl").doesNotExist())
                .andReturn();
        String sessionId = JsonTestSupport.readString(deliverySession.getResponse().getContentAsString(), "/deliverySessionId");

        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions/" + sessionId + "/report-pr")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-stranger"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prUrl": "http://localhost:3001/monopolyfun-projects/mf-20260510-review-loop/pulls/7",
                                  "headCommit": "abc123def456"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions/" + sessionId + "/report-pr")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prUrl": "http://localhost:3001/monopolyfun-projects/mf-20260510-review-loop/pulls/7",
                                  "headCommit": "abc123def456",
                                  "diffSummary": "首轮实现 PR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pr_reported"));

        insertProofAsset(orderNo, "asset-proof-review-001");
        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions/" + sessionId + "/finalize-proof")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "首轮 PR 已提交。",
                                  "artifacts": ["asset://asset-proof-review-001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(post("/api/v1/work/items/wb-lead-review-" + orderNo + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-stranger"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerAccountId": "acct-stranger",
                                  "decision": "accepted",
                                  "reason": "越权验收"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/work/items/wb-lead-review-" + orderNo + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerAccountId": "acct-founder",
                                  "decision": "revision_requested",
                                  "reason": "补充测试和说明"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("revision_requested"));
        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("claimed"))
                .andExpect(jsonPath("$.displayPhase").value("delivery_result_due"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select status from work_items where account_id = ? and item_no = ?",
                String.class,
                "acct-worker",
                "wb-delivery-result-" + orderNo)).isEqualTo("revision_requested");

        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions/" + sessionId + "/report-pr")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prUrl": "http://localhost:3001/monopolyfun-projects/mf-20260510-review-loop/pulls/7",
                                  "headCommit": "def456abc789",
                                  "diffSummary": "补充测试后的 PR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prUrl").value("http://localhost:3001/monopolyfun-projects/mf-20260510-review-loop/pulls/7"));

        insertProofAsset(orderNo, "asset-proof-review-002");
        mockMvc.perform(post("/api/v1/work/repo-delivery-sessions/" + sessionId + "/finalize-proof")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "返工 PR 已提交。",
                                  "artifacts": ["asset://asset-proof-review-002"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(post("/api/v1/work/items/wb-lead-review-" + orderNo + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerAccountId": "acct-founder",
                                  "decision": "accepted",
                                  "reason": "返工通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("accepted_open"))
                .andExpect(jsonPath("$.displayPhase").value("accepted_window_open"));
    }

    private void insertProofAsset(String orderNo, String assetId) {
        jdbcTemplate.update("""
                        insert into proof_assets (
                          id, order_id, artifact_ref, object_key, filename, content_type, content_length_bytes,
                          checksum_sha256, storage_provider, bucket, status, public_url, metadata, uploaded_by_account_id,
                          purpose, visibility, created_at, updated_at
                        ) values (
                          ?, ?, ?, ?, 'patch.diff', 'text/plain', 128,
                          'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'fake', 'proof-bucket',
                          'uploaded'::proof_asset_status, null, '{}'::jsonb, 'acct-worker',
                          'proof', 'participants', now(), now()
                        )
                        """,
                assetId,
                internalOrderId(orderNo),
                "asset://" + assetId,
                "orders/" + orderNo + "/" + assetId + ".diff");
    }

    private String orderNo(String orderId) {
        return jdbcTemplate.queryForObject("select order_no from orders where id = ? or order_no = ?", String.class, orderId, orderId);
    }

    private String internalOrderId(String orderNo) {
        return jdbcTemplate.queryForObject("select id from orders where order_no = ?", String.class, orderNo);
    }
}
