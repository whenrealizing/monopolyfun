package com.monopolyfun;

import com.monopolyfun.modules.project.infra.ProjectDevelopmentRepository;
import com.monopolyfun.modules.project.service.UrlHealthCheckService;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectValidationProtocolApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectDevelopmentRepository developmentRepository;

    @MockitoBean
    private RepoProviderClient repoProviderClient;

    @MockitoBean
    private UrlHealthCheckService urlHealthCheckService;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("""
                truncate table
                  project_validations,
                  business_id_sequences,
                  work_trust_events,
                  work_events,
                  work_reviews,
                  work_receipts,
                  work_runs,
                  work_items,
                  contribution_ledger,
                  project_memory_sync_events,
                  project_memory_repo_entries,
                  project_memory_repo_sources,
                  project_memory_repo_roots,
                  project_external_refs,
                  project_repo_bindings,
                  share_release_requests,
                  project_roles,
                  organization_events,
                  order_progress_updates,
                  offers,
                  requests,
                  projects,
                  order_events,
                  shares_ledger,
                  proofs,
                  orders,
                  listings,
                  markets,
                  accounts
                cascade
                """);
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values
                ('acct-founder', '@founder', 'Founder', '{}'::jsonb),
                ('acct-builder', '@builder', 'Builder', '{}'::jsonb),
                ('acct-reviewer', '@reviewer', 'Reviewer', '{}'::jsonb),
                ('acct-agent', '@agent', 'Agent', '{}'::jsonb)
                """);
        when(repoProviderClient.provisionPublicRepository(any())).thenReturn(new RepoProviderClient.ProvisionedRepository(
                "forgejo",
                "http://localhost:3001/whenrealizing/monopolyfun",
                "http://localhost:3001/whenrealizing/monopolyfun.git",
                "whenrealizing",
                "monopolyfun",
                "main",
                "public",
                java.util.Map.of("providerRepoId", "test-repo")));
        when(urlHealthCheckService.check("https://example.com/demo")).thenReturn(java.util.Map.of(
                "url", "https://example.com/demo",
                "reachable", true,
                "statusCode", 200,
                "latencyMs", 11,
                "checkedAt", "2026-05-19T00:00:00Z"));
    }

    @Test
    void launchTaskProofFeedbackAndRewardFollowProtocolLifecycle() throws Exception {
        String projectNo = projectNo(createProject());

        var launchResult = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "首轮公开验证",
                                  "hypothesis": "验证用户愿意使用 agent 生成投研报告。",
                                  "proofRequests": [
                                    {
                                      "title": "真实使用证据",
                                      "intent": "收集可审计的使用和反馈记录。",
                                      "evidenceRequirements": [
                                        {"kind": "usage_snapshot", "description": "用户完成一次报告生成"}
                                      ],
                                      "acceptanceSignals": [
                                        {"kind": "review_signal", "description": "Reviewer 确认证据可读"}
                                      ],
                                      "riskLevel": "normal",
                                      "metadata": {"source": "participant_draft"}
                                    }
                                  ],
                                  "sourceRefs": [
                                    {"kind": "discussion", "url": "https://example.com/thread"}
                                  ],
                                  "metadata": {"scorePolicyVersion": "score-v1"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.proofRequests.length()").value(1))
                .andReturn();
        String launchId = JsonTestSupport.readString(launchResult.getResponse().getContentAsString(), "/id");
        String proofRequestId = JsonTestSupport.readString(launchResult.getResponse().getContentAsString(), "/proofRequests/0/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/publish")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("live"));

        var feedbackResult = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/validation-feedback")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "launchId": "%s",
                                  "subjectType": "proof_request",
                                  "subjectId": "%s",
                                  "intent": "补充证据口径",
                                  "reason": "需要记录报告输入、输出和用户评价。",
                                  "evidence": [
                                    {"kind": "comment", "url": "https://example.com/review"}
                                  ],
                                  "suggestedAction": "在 Task 验收标准中加入用户评价截图",
                                  "metadata": {"reviewChannel": "public"}
                                }
                                """.formatted(launchId, proofRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andReturn();
        String feedbackId = JsonTestSupport.readString(feedbackResult.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/validation-feedback/" + feedbackId + "/resolve")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "resolved",
                                  "resolution": "已写入任务验收标准。",
                                  "metadata": {"linkedTaskDraft": true}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/validation-feedback")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subjectType": "project",
                                  "subjectId": "%s",
                                  "intent": "调整整体方向",
                                  "reason": "早期用户更关心报告可解释性。",
                                  "evidence": [],
                                  "suggestedAction": "新建一个解释性验证 Launch Card"
                                }
                                """.formatted(projectNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.subjectType").value("project"));

        var taskResult = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/tasks")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "交付可验证报告 demo",
                                  "intent": "完成一个可审计的 agent 报告生成流程。",
                                  "linkedProofRequestIds": ["%s"],
                                  "deliverable": "交付 demo 链接、报告样例、用户反馈截图和运行记录。",
                                  "acceptanceCriteria": [
                                    "demo 链接可访问",
                                    "报告样例可阅读",
                                    "用户反馈截图可追溯"
                                  ],
                                  "suggestedEvidence": [
                                    {"kind": "deployment_url"},
                                    {"kind": "screenshot"},
                                    {"kind": "run_log"}
                                  ],
                                  "rewardPreview": {"curveWeightHint": 1.25},
                                  "templateRef": "community/agent-demo-task@1",
                                  "tags": ["agent", "validation"],
                                  "metadata": {"draftedBy": "agent"}
                                }
                                """.formatted(proofRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.linkedProofRequestIds[0]").value(proofRequestId))
                .andReturn();
        String taskId = JsonTestSupport.readString(taskResult.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/tasks/" + taskId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("claimed"))
                .andExpect(jsonPath("$.claimedByAccountId").value("acct-builder"));

        var proofResult = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/tasks/" + taskId + "/proof")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "完成 demo、报告样例、用户反馈截图和运行记录。",
                                  "evidenceItems": [
                                    {"kind": "deployment_url", "url": "https://example.com/demo"},
                                    {"kind": "artifact", "url": "https://example.com/report.md"},
                                    {"kind": "screenshot", "url": "https://example.com/feedback.png"}
                                  ],
                                  "linkedProofRequestIds": ["%s"],
                                  "notes": "证据由 builder 提交，reviewer 独立验收。",
                                  "metadata": {"runId": "agent-run-001"}
                                }
                                """.formatted(proofRequestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.metadata.evidenceValidation.itemCount").value(3))
                .andExpect(jsonPath("$.metadata.evidenceValidation.urlCount").value(3))
                .andExpect(jsonPath("$.metadata.evidenceValidation.healthChecks[0].reachable").value(true))
                .andReturn();
        String proofId = JsonTestSupport.readString(proofResult.getResponse().getContentAsString(), "/id");
        jdbcTemplate.update("update work_receipts set created_at = now() - interval '48 hours' where id = ?", proofId);

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(proofId))
                .andExpect(jsonPath("$[0].status").value("submitted"));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/review-queue")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].proof.id").value(proofId))
                .andExpect(jsonPath("$[0].launchTitle").value("首轮公开验证"))
                .andExpect(jsonPath("$[0].proof.validationStats.participantCount").value(0))
                .andExpect(jsonPath("$[0].proof.validationStats.minParticipantCount").value(1))
                .andExpect(jsonPath("$[0].reviewRewardPreview.participantProgress").value("0/1"));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/review-queue")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/proofs/" + proofId + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "accept",
                                  "reason": "自审应被阻止。",
                                  "requestedEvidence": [],
                                  "riskFlags": [],
                                  "scoreInputs": {"proofQuality": 1}
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/proofs/" + proofId + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "accept",
                                  "reason": "证据满足本轮 proofRequest。",
                                  "validationMode": "ordinary",
                                  "requestedEvidence": [],
                                  "riskFlags": [],
                                  "scoreInputs": {
                                    "proofQuality": 0.9,
                                    "feedbackStrength": 0.7,
                                    "curveWeight": 1.25
                                  },
                                  "metadata": {"reviewRound": 1}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.validationStats.participantCount").value(1))
                .andExpect(jsonPath("$.validationStats.ordinaryValidationCount").value(1))
                .andExpect(jsonPath("$.validationStats.effectiveValidationCount").value(1.0));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/rewards")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from work_events
                where subject_type = 'project_validation_reward'
                  and output_snapshot->>'recipientAccountId' = 'acct-builder'
                  and output_snapshot->'rewardSnapshot'->>'role' = 'proof_submitter'
                  and output_snapshot->>'status' = 'pending'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from work_events
                where subject_type = 'project_validation_reward'
                  and output_snapshot->>'recipientAccountId' = 'acct-reviewer'
                  and output_snapshot->'rewardSnapshot'->>'role' = 'proof_validator'
                  and output_snapshot->>'status' = 'pending'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select (output_snapshot->>'contributionWeight')::numeric
                from work_events
                where subject_type = 'project_validation_reward'
                  and output_snapshot->>'recipientAccountId' = 'acct-reviewer'
                """, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("0.500000"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/settle")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "首轮验证完成，统一结算奖励。",
                                  "scoreSnapshot": {"launchScore": 82},
                                  "curveSnapshot": {"curveVersion": "curve-v1", "weight": 1.25},
                                  "rewardSnapshot": {"poolShares": 1250, "unit": "project_share"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("settled"))
                .andExpect(jsonPath("$.metadata.scoreSnapshot.launchScore").value(82));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/rewards")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("settled"))
                .andExpect(jsonPath("$[1].status").value("settled"));

        BigDecimal submitterAmount = jdbcTemplate.queryForObject("""
                select (output_snapshot->'rewardSnapshot'->>'settledAmount')::numeric
                from work_events
                where subject_type = 'project_validation_reward'
                  and output_snapshot->>'recipientAccountId' = 'acct-builder'
                """, BigDecimal.class);
        BigDecimal reviewerAmount = jdbcTemplate.queryForObject("""
                select (output_snapshot->'rewardSnapshot'->>'settledAmount')::numeric
                from work_events
                where subject_type = 'project_validation_reward'
                  and output_snapshot->>'recipientAccountId' = 'acct-reviewer'
                """, BigDecimal.class);
        assertThat(submitterAmount).isGreaterThan(reviewerAmount);
        assertThat(reviewerAmount).isGreaterThan(new BigDecimal("500"));
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from contribution_ledger contribution
                join projects project on project.id = contribution.project_id
                where project.project_no = ?
                  and contribution.source_type = 'validation_reward'
                  and contribution.contribution_role in ('proof_submitter', 'proof_validator')
                """, Integer.class, projectNo)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from shares_ledger ledger
                join projects project on project.id = ledger.project_id
                where project.project_no = ?
                  and ledger.source_type = 'validation_reward'
                  and ledger.reason::text in ('validation_submitter', 'validation_validator')
                """, Integer.class, projectNo)).isEqualTo(2);

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/commercialization")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contributionLedger.length()").value(2))
                .andExpect(jsonPath("$.contributors.length()").value(2))
                .andExpect(jsonPath("$.contributionLedger[0].sourceType").value("validation_reward"))
                .andExpect(jsonPath("$.currentDistribution.eligibleShareMinted").value(org.hamcrest.Matchers.greaterThan(0)));

        assertThat(jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from work_events event
                        join projects project on project.id = event.input_snapshot->>'projectId'
                        where project.project_no = ?
                          and event.action_id = 'project_validation_protocol'
                        """,
                Integer.class,
                projectNo)).isGreaterThanOrEqualTo(8);
    }

    @Test
    void reviewProofHoldKeepsTaskInRevisionStateWithRiskContext() throws Exception {
        String projectNo = projectNo(createProject());

        var launchResult = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "风险复核验证",
                                  "hypothesis": "高风险证据需要挂起复核。",
                                  "proofRequests": [],
                                  "sourceRefs": [],
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String launchId = JsonTestSupport.readString(launchResult.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/publish")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk());

        var taskResult = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/tasks")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "补充风险证据",
                                  "intent": "提交可复核的风险证据。",
                                  "linkedProofRequestIds": [],
                                  "deliverable": "提交风险说明和链接。",
                                  "acceptanceCriteria": ["证据可读"],
                                  "suggestedEvidence": [{"kind": "risk_note"}],
                                  "rewardPreview": {},
                                  "templateRef": "risk/hold@1",
                                  "tags": ["risk"],
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = JsonTestSupport.readString(taskResult.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/tasks/" + taskId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder")))
                .andExpect(status().isOk());

        var proofResult = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/tasks/" + taskId + "/proof")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "提交风险证据初稿。",
                                  "evidenceItems": [{"kind": "risk_note", "url": "https://example.com/demo"}],
                                  "linkedProofRequestIds": [],
                                  "notes": "等待复核。",
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String proofId = JsonTestSupport.readString(proofResult.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/proofs/" + proofId + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "hold",
                                  "reason": "证据需要风险复核。",
                                  "requestedEvidence": [{"description": "补充风险来源"}],
                                  "riskFlags": ["source_unclear"],
                                  "scoreInputs": {"risk": 0.8},
                                  "metadata": {"reviewRound": 1}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("held"));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/tasks")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("changes_requested"))
                .andExpect(jsonPath("$[0].subStatus").value("review_hold"))
                .andExpect(jsonPath("$[0].metadata.riskFlags[0]").value("source_unclear"));
    }

    @Test
    void candidatePoolOnlyIncludesCompletedTasksWithMergeablePullRequests() throws Exception {
        String projectId = createProject();
        String projectNo = projectNo(projectId);
        String launchId = "launch-candidate";
        String acceptedTaskId = "vtask-candidate-ready";
        String openTaskId = "vtask-candidate-open";
        jdbcTemplate.update("""
                insert into project_validations (
                  id, project_id, title, hypothesis, status, source_refs, metadata, created_by_account_id
                ) values (?, ?, '候选池验证', '验证已完成 PR 才能进入候选池。', 'live', '[]'::jsonb, '{}'::jsonb, 'acct-founder')
                """, launchId, projectId);
        insertValidationTask(projectId, launchId, acceptedTaskId, "accepted");
        insertValidationTask(projectId, launchId, openTaskId, "open");
        insertPullRequest(projectId, acceptedTaskId, 128, "open", """
                {"mergeable": true, "mergeable_state": "clean", "draft": false}
                """);
        insertCiCheck(projectId, acceptedTaskId, 128, "completed", "success");
        insertPullRequest(projectId, openTaskId, 129, "open", """
                {"mergeable": true, "mergeable_state": "clean", "draft": false}
                """);
        insertCiCheck(projectId, openTaskId, 129, "completed", "success");

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/development/candidates")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].taskId").value(acceptedTaskId))
                .andExpect(jsonPath("$.items[0].taskCompleted").value(true))
                .andExpect(jsonPath("$.items[0].candidateStatus").value("candidate_ready"))
                .andExpect(jsonPath("$.items[0].consensusStatus").value("support_open"))
                .andExpect(jsonPath("$.items[0].mergeabilityStatus").value("ready"))
                .andExpect(jsonPath("$.items[0].ciPassed").value(true))
                .andExpect(jsonPath("$.items[0].prNumber").value(128));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/development/candidates/" + acceptedTaskId + "/final-review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prNumber": 128,
                                  "decision": "accepted",
                                  "reason": "最终 diff 已复核。"
                                }
                                """))
                .andExpect(status().isConflict());

        supportCandidate(projectNo, acceptedTaskId, 128, "acct-founder")
                .andExpect(jsonPath("$.supportCount").value(1))
                .andExpect(jsonPath("$.consensusStatus").value("support_open"));
        supportCandidate(projectNo, acceptedTaskId, 128, "acct-builder")
                .andExpect(jsonPath("$.supportCount").value(2))
                .andExpect(jsonPath("$.consensusStatus").value("support_open"));
        supportCandidate(projectNo, acceptedTaskId, 128, "acct-reviewer")
                .andExpect(jsonPath("$.supportCount").value(3))
                .andExpect(jsonPath("$.consensusStatus").value("final_review_required"))
                .andExpect(jsonPath("$.finalReviewStatus").value("required"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/development/candidates/" + acceptedTaskId + "/final-review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prNumber": 128,
                                  "decision": "accepted",
                                  "reason": "最终 diff 已复核。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalReviewStatus").value("passed"))
                .andExpect(jsonPath("$.reviewedCommitSha").value("abc123"))
                .andExpect(jsonPath("$.consensusStatus").value("consensus_ready"));

        developmentRepository.savePrLink(
                projectId,
                acceptedTaskId,
                "http://localhost:3001/whenrealizing/monopolyfun",
                128,
                "http://localhost:3001/whenrealizing/monopolyfun/pulls/128",
                "newhead456",
                "main",
                "feature/" + acceptedTaskId,
                "open",
                java.util.Map.of("mergeable", true, "mergeable_state", "clean", "draft", false));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/development/candidates")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].taskId").value(acceptedTaskId))
                .andExpect(jsonPath("$.items[0].finalReviewStatus").value("stale"))
                .andExpect(jsonPath("$.items[0].consensusStatus").value("final_review_required"));
    }

    @Test
    void candidatePoolAllowsAcceptedNonCodeTasksWithoutPullRequests() throws Exception {
        String projectId = createProject();
        String projectNo = projectNo(projectId);
        String launchId = "launch-growth-candidate";
        String growthTaskId = "vtask-growth-candidate";
        jdbcTemplate.update("""
                insert into project_validations (
                  id, project_id, title, hypothesis, status, source_refs, metadata, created_by_account_id
                ) values (?, ?, '增长候选池', '验证增长和运营类任务通过验收后可以进入候选池。', 'live', '[]'::jsonb, '{}'::jsonb, 'acct-founder')
                """, launchId, projectId);
        insertValidationTask(projectId, launchId, growthTaskId, "accepted", "experiment_report");

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/development/candidates")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].taskId").value(growthTaskId))
                .andExpect(jsonPath("$.items[0].resultType").value("evidence"))
                .andExpect(jsonPath("$.items[0].candidateStatus").value("candidate_ready"))
                .andExpect(jsonPath("$.items[0].mergeabilityStatus").value("not_required"))
                .andExpect(jsonPath("$.items[0].ciStatus").value("not_required"))
                .andExpect(jsonPath("$.items[0].consensusStatus").value("support_open"));

        supportCandidateWithoutPr(projectNo, growthTaskId, "acct-founder");
        supportCandidateWithoutPr(projectNo, growthTaskId, "acct-builder");
        supportCandidateWithoutPr(projectNo, growthTaskId, "acct-reviewer")
                .andExpect(jsonPath("$.supportCount").value(3))
                .andExpect(jsonPath("$.consensusStatus").value("final_review_required"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/development/candidates/" + growthTaskId + "/final-review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "accepted",
                                  "reason": "增长实验报告已复核。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalReviewStatus").value("passed"))
                .andExpect(jsonPath("$.consensusStatus").value("consensus_ready"));
    }

    @Test
    void candidateWindowUsesSmallAgentContextAndSkipLeases() throws Exception {
        String projectId = createProject();
        String projectNo = projectNo(projectId);
        String launchId = "launch-window-candidate";
        String firstTaskId = "vtask-window-first";
        String secondTaskId = "vtask-window-second";
        jdbcTemplate.update("""
                insert into project_validations (
                  id, project_id, title, hypothesis, status, source_refs, metadata, created_by_account_id
                ) values (?, ?, '候选窗口', '验证 agent 只拿当前小窗口并可跳过。', 'live', '[]'::jsonb, '{}'::jsonb, 'acct-founder')
                """, launchId, projectId);
        insertValidationTask(projectId, launchId, firstTaskId, "accepted", "experiment_report");
        insertValidationTask(projectId, launchId, secondTaskId, "accepted", "metric_snapshot");
        jdbcTemplate.update("update work_items set updated_at = now() - interval '2 minutes' where id = ?", firstTaskId);
        jdbcTemplate.update("update work_items set updated_at = now() - interval '1 minute' where id = ?", secondTaskId);

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/development/candidates")
                        .param("limit", "1")
                        .param("status", "candidate_ready")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.summary.candidateReady").value(2));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/development/candidates/next")
                        .param("limit", "1")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.length()").value(1))
                .andExpect(jsonPath("$.current[0].candidateId").value("candidate-" + firstTaskId + "-no-pr"))
                .andExpect(jsonPath("$.current[0].reasonToAct").value("needs_support"))
                .andExpect(jsonPath("$.current[0].nextAction").value("support"))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.blockedSummary.integrationChecking").value(0))
                .andExpect(jsonPath("$.historySummary.mergedMainline").value(0));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/development/candidates/window-skip")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skippedCandidateIds": ["candidate-%s-no-pr"],
                                  "reasonCode": "missing_domain_context",
                                  "reason": "缺少增长实验上下文",
                                  "ttlMinutes": 30
                                }
                                """.formatted(firstTaskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current[0].candidateId").value("candidate-" + secondTaskId + "-no-pr"));
    }

    @Test
    void agentProtocolUsesTinyInboxAndSettlesAcceptedProposalPackShares() throws Exception {
        String projectId = createProject();
        String projectNo = projectNo(projectId);

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/agent/inbox")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].type").value("submit_pack"))
                .andExpect(jsonPath("$.cards[0].context.mode").value("proposal_pack_with_implementation"));

        var submit = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "submit_pack",
                                  "payload": {
                                    "title": "AI 搜索 MVP Pack",
                                    "summary": "搜索框、结果列表和 LLM 摘要。",
                                    "work": {
                                      "objective": "做出可演示的 AI 搜索 MVP",
                                      "scope": ["search page", "result list", "ai summary"],
                                      "acceptanceCriteria": ["可以输入 query", "可以展示摘要"]
                                    },
                                    "implementation": {
                                      "type": "code",
                                      "summary": "已提交搜索页面实现"
                                    },
                                    "artifacts": [
                                      {"kind": "pull_request", "url": "http://localhost:3001/org/repo/pulls/12", "headSha": "packhead123", "mergeable": true, "checksConclusion": "success"},
                                      {"kind": "screenshot", "url": "https://example.com/search.png"},
                                      {"kind": "test_result", "summary": "mvn test passed"}
                                    ],
                                    "initialImpact": {
                                      "scope": 80,
                                      "complexity": 70,
                                      "leverage": 85,
                                      "evidence": 90
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scoring"))
                .andExpect(jsonPath("$.inbox.project.goal").value("围绕 Launch Card 完成任务、证据、反馈和奖励结算。"))
                .andReturn();
        String packId = JsonTestSupport.readString(submit.getResponse().getContentAsString(), "/packId");

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/agent/inbox")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].type").value("score_review"))
                .andExpect(jsonPath("$.cards[0].context.artifactCount").value(3))
                .andExpect(jsonPath("$.cards[0].context.summary").value("搜索框、结果列表和 LLM 摘要。"))
                .andExpect(jsonPath("$.cards[0].context.work").doesNotExist())
                .andExpect(jsonPath("$.cards[0].context.implementation").doesNotExist());

        scorePack(projectNo, packId, "acct-builder", 80, 80, 80, 80)
                .andExpect(jsonPath("$.status").value("scoring"));
        scorePack(projectNo, packId, "acct-reviewer", 82, 82, 82, 82)
                .andExpect(jsonPath("$.status").value("scoring"));
        scorePack(projectNo, packId, "acct-agent", 81, 81, 81, 81)
                .andExpect(jsonPath("$.status").value("score_stable"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "result_review",
                                  "payload": {
                                    "packId": "%s",
                                    "decision": "accepted",
                                    "reason": "PR、截图和测试结果齐全。"
                                  }
                                }
                                """.formatted(packId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("certified"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "final_review",
                                  "payload": {
                                    "packId": "%s",
                                    "decision": "accepted",
                                    "reviewedHeadSha": "packhead123",
                                    "reason": "稳定分和产物认证通过。"
                                  }
                                }
                                """.formatted(packId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.result.sharePool").value(5314));

        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                from work_events
                where subject_type = 'project_proposal_pack_share_allocation'
                  and subject_id = ?
                """, Integer.class, packId)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("""
                select coalesce(sum(amount), 0)
                from shares_ledger
                where source_type = 'proposal_pack'
                  and source_id = ?
                """, Integer.class, packId)).isEqualTo(5314);
    }

    @Test
    void agentProtocolRevisesChallengedPackAndRequiresHeadShaSnapshot() throws Exception {
        String projectId = createProject();
        String projectNo = projectNo(projectId);
        var submit = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "submit_pack",
                                  "payload": {
                                    "title": "Search Fix Pack",
                                    "summary": "首版搜索实现。",
                                    "work": {"objective": "ship search"},
                                    "implementation": {"type": "code", "summary": "first PR"},
                                    "artifacts": [
                                      {"kind": "pull_request", "url": "http://localhost:3001/org/repo/pulls/13", "headSha": "oldsha", "mergeable": true, "checksConclusion": "success"}
                                    ],
                                    "initialImpact": {"scope": 70, "complexity": 70, "leverage": 70, "evidence": 70}
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String packId = JsonTestSupport.readString(submit.getResponse().getContentAsString(), "/packId");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "challenge_pack",
                                  "payload": {
                                    "packId": "%s",
                                    "reason": "摘要页有 bug。",
                                    "suggestedPatch": {"file": "search.tsx"}
                                  }
                                }
                                """.formatted(packId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("challenged"));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/agent/inbox")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].type").value("revise_pack"))
                .andExpect(jsonPath("$.cards[0].context.revision").value(0));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "revise_pack",
                                  "payload": {
                                    "packId": "%s",
                                    "reason": "修复摘要页 bug。",
                                    "implementation": {"type": "code", "summary": "bug fix PR"},
                                    "artifacts": [
                                      {"kind": "pull_request", "url": "http://localhost:3001/org/repo/pulls/14", "headSha": "newsha", "mergeable": true, "checksConclusion": "success"}
                                    ]
                                  }
                                }
                                """.formatted(packId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scoring"))
                .andExpect(jsonPath("$.result.revision").value(1));

        scorePack(projectNo, packId, "acct-builder", 84, 84, 84, 84);
        scorePack(projectNo, packId, "acct-reviewer", 84, 84, 84, 84);
        scorePack(projectNo, packId, "acct-agent", 84, 84, 84, 84)
                .andExpect(jsonPath("$.status").value("score_stable"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType": "result_review", "payload": {"packId": "%s", "decision": "accepted", "reason": "revised evidence ok"}}
                                """.formatted(packId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("certified"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType": "final_review", "payload": {"packId": "%s", "decision": "accepted", "reviewedHeadSha": "oldsha", "reason": "stale review"}}
                                """.formatted(packId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-builder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actionType": "final_review", "payload": {"packId": "%s", "decision": "accepted", "reviewedHeadSha": "newsha", "reason": "current head reviewed"}}
                                """.formatted(packId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));
    }

    @Test
    void agentProtocolRejectsOversizedProposalPackJson() throws Exception {
        String projectId = createProject();
        String projectNo = projectNo(projectId);

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "submit_pack",
                                  "payload": {
                                    "title": "Oversized Pack",
                                    "summary": "Too many artifacts.",
                                    "work": {"objective": "keep context small"},
                                    "implementation": {"summary": "implementation attached by reference"},
                                    "artifacts": [
                                      {"kind": "ref", "url": "https://example.com/1"},
                                      {"kind": "ref", "url": "https://example.com/2"},
                                      {"kind": "ref", "url": "https://example.com/3"},
                                      {"kind": "ref", "url": "https://example.com/4"},
                                      {"kind": "ref", "url": "https://example.com/5"},
                                      {"kind": "ref", "url": "https://example.com/6"},
                                      {"kind": "ref", "url": "https://example.com/7"},
                                      {"kind": "ref", "url": "https://example.com/8"},
                                      {"kind": "ref", "url": "https://example.com/9"}
                                    ],
                                    "initialImpact": {
                                      "scope": 80,
                                      "complexity": 70,
                                      "leverage": 85,
                                      "evidence": 90
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void candidatePoolRechecksOpenCandidatesWhenPullRequestMerges() throws Exception {
        String projectId = createProject();
        String projectNo = projectNo(projectId);
        String repoUrl = "http://localhost:3001/whenrealizing/monopolyfun";
        String launchId = "launch-candidate-recheck";
        String mergedTaskId = "vtask-candidate-merged";
        String recheckTaskId = "vtask-candidate-recheck";
        jdbcTemplate.update("""
                insert into project_validations (
                  id, project_id, title, hypothesis, status, source_refs, metadata, created_by_account_id
                ) values (?, ?, '候选池重检', '验证主线合并后其他候选需要重新检查。', 'live', '[]'::jsonb, '{}'::jsonb, 'acct-founder')
                """, launchId, projectId);
        insertValidationTask(projectId, launchId, mergedTaskId, "accepted");
        insertValidationTask(projectId, launchId, recheckTaskId, "accepted");
        insertPullRequest(projectId, mergedTaskId, 128, "open", """
                {"mergeable": true, "mergeable_state": "clean", "draft": false}
                """);
        insertPullRequest(projectId, recheckTaskId, 129, "open", """
                {"mergeable": true, "mergeable_state": "clean", "draft": false}
                """);
        insertCiCheck(projectId, mergedTaskId, 128, "completed", "success");
        insertCiCheck(projectId, recheckTaskId, 129, "completed", "success");

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/development/candidates")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].candidateStatus").value("candidate_ready"))
                .andExpect(jsonPath("$.items[1].candidateStatus").value("candidate_ready"));

        developmentRepository.savePrLink(
                projectId,
                mergedTaskId,
                repoUrl,
                128,
                repoUrl + "/pulls/128",
                "def456",
                "main",
                "feature/" + mergedTaskId,
                "merged",
                java.util.Map.of("merged", true));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/development/candidates")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].taskId").value(recheckTaskId))
                .andExpect(jsonPath("$.items[0].candidateStatus").value("integration_checking"))
                .andExpect(jsonPath("$.items[0].mergeabilityReason").value("mainline changed after this candidate was checked"))
                .andExpect(jsonPath("$.items[1].taskId").value(mergedTaskId))
                .andExpect(jsonPath("$.items[1].candidateStatus").value("merged_mainline"));
    }

    private String createProject() throws Exception {
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Agent 产品验证协议",
                                  "description": "用参与者提交的证据推进产品验证。",
                                  "goal": "围绕 Launch Card 完成任务、证据、反馈和奖励结算。",
                                  "items": [
                                    {
                                      "name": "初始化协作入口",
                                      "deliveryStandard": "提交可审计的协作入口。",
                                      "acceptanceCriteria": ["入口可访问", "证据可追溯"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");
    }

    private String projectNo(String projectId) {
        return jdbcTemplate.queryForObject("select project_no from projects where id = ?", String.class, projectId);
    }

    private void insertValidationTask(String projectId, String launchId, String taskId, String taskStatus) {
        insertValidationTask(projectId, launchId, taskId, taskStatus, "pull_request");
    }

    private void insertValidationTask(String projectId, String launchId, String taskId, String taskStatus, String evidenceKind) {
        String workStatus = switch (taskStatus) {
            case "accepted", "settled" -> "accepted";
            case "proof_submitted" -> "submitted";
            case "claimed", "working" -> "claimed";
            default -> "ready";
        };
        jdbcTemplate.update("""
                insert into work_items (
                  id, item_no, source_type, source_id, account_id, title, goal,
                  acceptance_criteria, input_refs, output_schema, required_role, urgency, status, ready_at, created_at, updated_at
                ) values (?, ?, 'project_validation_task', ?, 'acct-builder', ?, '交付可合并 PR。',
                  '["CI 通过"]'::jsonb,
                  jsonb_build_array('project:' || ?, 'launch:' || ?),
                  jsonb_build_object(
                    'projectId', ?,
                    'launchId', ?,
                    'intent', '提交可合并代码结果',
                    'deliverable', '交付可合并 PR。',
                    'linkedProofRequestIds', '[]'::jsonb,
                    'suggestedEvidence', jsonb_build_array(jsonb_build_object('kind', ?)),
                    'rewardPreview', '{}'::jsonb,
                    'templateRef', '',
                    'taskStatus', ?,
                    'subStatus', '',
                    'tags', '[]'::jsonb,
                    'metadata', '{}'::jsonb,
                    'createdByAccountId', 'acct-builder',
                    'claimedByAccountId', 'acct-builder',
                    'claimedAt', ''
                  ),
                  'project_validator', 'attention', ?, now(), now(), now())
                """, taskId, taskId, launchId, "任务 " + taskId, projectId, launchId, projectId, launchId, evidenceKind, taskStatus, workStatus);
    }

    private void insertPullRequest(String projectId, String taskId, int prNumber, String state, String payloadJson) {
        jdbcTemplate.update("""
                        insert into project_external_refs (
                          id, ref_type, project_id, validation_task_id, repo_url, pr_number, pr_url, head_sha, base_branch, branch_name, state,
                          last_synced_at, raw_payload
                        ) values (?, 'pull_request', ?, ?, 'http://localhost:3001/whenrealizing/monopolyfun', ?, ?, 'abc123', 'main', ?, ?, now(), ?::jsonb)
                        """, "ppr-" + taskId, projectId, taskId, prNumber,
                "http://localhost:3001/whenrealizing/monopolyfun/pulls/" + prNumber,
                "feature/" + taskId, state, payloadJson);
    }

    private void insertCiCheck(String projectId, String taskId, int prNumber, String status, String conclusion) {
        jdbcTemplate.update("""
                insert into project_external_refs (
                  id, ref_type, project_id, validation_task_id, pr_number, check_name, status, conclusion, details_url, completed_at, raw_payload
                ) values (?, 'ci_check', ?, ?, ?, 'api-test', ?, ?, 'http://localhost:3001/whenrealizing/monopolyfun/actions/runs/1', now(), '{}'::jsonb)
                """, "pci-" + taskId, projectId, taskId, prNumber, status, conclusion);
    }

    private org.springframework.test.web.servlet.ResultActions supportCandidate(String projectNo, String taskId, int prNumber, String accountId) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectNo + "/development/candidates/" + taskId + "/support")
                        .with(SecurityTestSupport.session(jdbcTemplate, accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prNumber": %d,
                                  "reason": "认可这个结果。"
                                }
                                """.formatted(prNumber)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions supportCandidateWithoutPr(String projectNo, String taskId, String accountId) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectNo + "/development/candidates/" + taskId + "/support")
                        .with(SecurityTestSupport.session(jdbcTemplate, accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "认可这个结果。"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions scorePack(
            String projectNo,
            String packId,
            String accountId,
            int scope,
            int complexity,
            int leverage,
            int evidence) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectNo + "/agent/actions")
                        .with(SecurityTestSupport.session(jdbcTemplate, accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "score_review",
                                  "payload": {
                                    "packId": "%s",
                                    "choice": "endorse",
                                    "scores": {
                                      "scope": %d,
                                      "complexity": %d,
                                      "leverage": %d,
                                      "evidence": %d
                                    },
                                    "reason": "结构完整，证据可验证。"
                                  }
                                }
                                """.formatted(packId, scope, complexity, leverage, evidence)))
                .andExpect(status().isOk());
    }
}
