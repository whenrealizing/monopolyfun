package com.monopolyfun;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.monopolyfun.modules.project.service.RootProjectService;
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

import java.util.List;
import java.util.Map;

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
class ProjectMemoryApiTest extends AbstractPostgresIntegrationTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RootProjectService rootProjectService;

    @MockitoBean
    private RepoProviderClient repoProviderClient;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("truncate table business_id_sequences, project_validations, project_memory_sync_events, project_memory_repo_entries, project_memory_repo_sources, project_memory_repo_roots, work_trust_events, work_events, work_reviews, work_receipts, work_runs, work_items, project_external_refs, project_repo_bindings, share_release_requests, project_roles, organization_events, order_progress_updates, offers, requests, projects, order_events, shares_ledger, proofs, orders, listings, markets, accounts cascade");
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values
                ('acct-founder', '@founder', 'Founder', '{}'::jsonb),
                ('acct-worker', '@worker', 'Worker', '{}'::jsonb)
                """);
        when(repoProviderClient.provisionPublicRepository(any())).thenReturn(new RepoProviderClient.ProvisionedRepository(
                "forgejo",
                "http://localhost:3001/whenrealizing/monopolyfun",
                "http://localhost:3001/whenrealizing/monopolyfun.git",
                "whenrealizing",
                "monopolyfun",
                "main",
                "public",
                java.util.Map.of("providerRepoId", "project-memory-test-repo")));
    }

    @Test
    void teamMaintainsMemoryFromSystemSourceAndAgentContextReadsOnlyApprovedEntries() throws Exception {
        String projectNo = projectNo(publishProject());

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/memory/sources")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "src_growth_review",
                                  "kind": "growth_result",
                                  "path": ".monopoly-memory/sources/growth-review.md",
                                  "visibility": "team",
                                  "externalUrl": "https://example.com/growth-review",
                                  "metadata": {"summary": "6 replies, 2 interviews"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value("src_growth_review"));

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/memory/entries")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memoryId": "mem_trust_lesson",
                                  "kind": "lesson",
                                  "content": "目标用户更关心任务验收是否可信。",
                                  "sourceRefs": ["src_growth_review"],
                                  "confidence": 0.8,
                                  "visibility": "team"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("proposed"));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/agent-context")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memory.lesson").doesNotExist());

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/memory/entries/mem_trust_lesson/approve")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/agent-context")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memory.lesson[0].content").value("目标用户更关心任务验收是否可信。"))
                .andExpect(jsonPath("$.memory.lesson[0].sourceRefs[0]").value("src_growth_review"));
    }

    @Test
    void validationTaskAppearsInAgentContextAfterAcceptedProof() throws Exception {
        String projectNo = projectNo(publishProject());

        var launchCreate = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "首轮增长验证",
                                  "hypothesis": "首轮内容增长可以带来访谈线索。",
                                  "proofRequests": [
                                    {
                                      "title": "实验复盘",
                                      "intent": "证明增长实验结果",
                                      "evidenceRequirements": [{"kind": "experiment_report"}],
                                      "acceptanceSignals": [{"kind": "interview_leads"}]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String launchId = JsonTestSupport.readString(launchCreate.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/publish")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        var taskCreate = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/launches/" + launchId + "/tasks")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "首轮增长实验",
                                  "intent": "growth_experiment",
                                  "deliverable": "提交实验数据和复盘。",
                                  "acceptanceCriteria": ["提交实验数据和复盘"],
                                  "suggestedEvidence": [{"kind": "experiment_report"}],
                                  "rewardPreview": {"unit": "project_share"}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String taskId = JsonTestSupport.readString(taskCreate.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/tasks/" + taskId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        var proofCreate = mockMvc.perform(post("/api/v1/projects/" + projectNo + "/tasks/" + taskId + "/proof")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summary": "发布 3 条内容，获得 6 条回复和 2 个访谈意向。",
                                  "evidenceItems": [{"kind": "experiment_report", "url": "https://example.com/growth/experiment-001"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String proofId = JsonTestSupport.readString(proofCreate.getResponse().getContentAsString(), "/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/proofs/" + proofId + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "accept",
                                  "reason": "复盘证据完整。",
                                  "scoreInputs": {"impact": 1}
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/agent-context")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.acceptedTasks[0].taskId").value(taskId))
                .andExpect(jsonPath("$.validation.rewards[0].status").value("pending"));
    }

    @Test
    void sourceContractHashIsStableAndExcludesItsOwnHashField() throws Exception {
        String projectNo = projectNo(publishProject());

        createSource(projectNo, "src_hash_z", ".monopoly-memory/sources/hash-z.md");
        createSource(projectNo, "src_hash_a", ".monopoly-memory/sources/hash-a.md");
        createAndApproveEntry(projectNo, "mem_hash_z", "src_hash_z");
        createAndApproveEntry(projectNo, "mem_hash_a", "src_hash_a");

        String first = mockMvc.perform(get("/api/v1/projects/" + projectNo + "/memory/source-contract")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String second = mockMvc.perform(get("/api/v1/projects/" + projectNo + "/memory/source-contract")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> firstContract = OBJECT_MAPPER.readValue(first, new TypeReference<>() {
        });
        Map<String, Object> secondContract = OBJECT_MAPPER.readValue(second, new TypeReference<>() {
        });
        String hash = String.valueOf(firstContract.get("contractHash"));
        assertThat(hash).isEqualTo(secondContract.get("contractHash"));
        assertThat(hash).hasSize(64);
        assertThat(ids(firstContract, "sources", "sourceId")).containsExactly("src_hash_a", "src_hash_z");
        assertThat(ids(firstContract, "activeMemory", "memoryId")).containsExactly("mem_hash_a", "mem_hash_z");
        firstContract.put("contractHash", "tampered");
        assertThat(firstContract).isNotEqualTo(secondContract);
    }

    private void createSource(String projectNo, String sourceId, String path) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/memory/sources")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "%s",
                                  "kind": "lesson",
                                  "path": "%s",
                                  "visibility": "team",
                                  "metadata": {"summary": "hash source"}
                                }
                                """.formatted(sourceId, path)))
                .andExpect(status().isOk());
    }

    private void createAndApproveEntry(String projectNo, String memoryId, String sourceRef) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/memory/entries")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memoryId": "%s",
                                  "kind": "lesson",
                                  "content": "hash 只覆盖 contract body。",
                                  "sourceRefs": ["%s"],
                                  "confidence": 0.9,
                                  "visibility": "team"
                                }
                                """.formatted(memoryId, sourceRef)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/memory/entries/" + memoryId + "/approve")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private List<String> ids(Map<String, Object> contract, String listKey, String idKey) {
        return ((List<?>) contract.get(listKey)).stream()
                .map(item -> String.valueOf(((Map<?, ?>) item).get(idKey)))
                .toList();
    }

    private String publishProject() throws Exception {
        // 中文注释：Project Memory 是 Root Project 维护面，测试直接初始化 root 聚合并复用真实权限投影。
        return rootProjectService.ensureRootProject("acct-founder").id();
    }

    private String projectNo(String projectId) {
        return jdbcTemplate.queryForObject("select project_no from projects where id = ?", String.class, projectId);
    }

}
