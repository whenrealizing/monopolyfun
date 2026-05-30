package com.monopolyfun;

import com.monopolyfun.modules.identity.service.security.RateLimitService;
import com.monopolyfun.modules.order.service.command.OrderCommandService;
import com.monopolyfun.modules.project.service.ProjectLifecycleService;
import com.monopolyfun.modules.repo.infra.RepoProviderClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectPostItemWorkflowApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProjectLifecycleService projectLifecycleService;

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private RateLimitService rateLimitService;

    @MockitoBean
    private RepoProviderClient repoProviderClient;

    @BeforeEach
    void resetSchema() {
        rateLimitService.clear();
        jdbcTemplate.execute("truncate table business_id_sequences, risk_events, audit_events, share_release_requests, project_roles, organization_events, order_progress_updates, offers, requests, projects, order_events, shares_ledger, proofs, orders, listings, markets, accounts cascade");
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values
                ('acct-founder', '@founder', 'Founder', '{}'::jsonb),
                ('acct-worker', '@worker', 'Worker', '{}'::jsonb),
                ('acct-cfo', '@cfo', 'CFO', '{}'::jsonb)
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
    }

    @Test
    void createItemClaimAndCompleteThroughWorkbench() throws Exception {
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "交易情报公司",
                                  "description": "Founder 创建 project 后，直接围绕多个 item 持续推进。",
                                  "goal": "先完成任务看板、shares 曲线和 agent 交付机制。",
                                  "items": [
                                    {
                                      "name": "初始化项目任务",
                                      "description": "发布时自带的首个 item。",
                                      "deliveryStandard": "交付项目任务说明和执行摘要。",
                                      "acceptanceCriteria": ["说明文档可阅读", "执行摘要覆盖任务结果"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id").isNotEmpty())
                .andExpect(jsonPath("$.project.projectNo").isNotEmpty())
                .andExpect(jsonPath("$.project.projectLevel").value("child"))
                .andExpect(jsonPath("$.project.parentProjectId").value("project-root"))
                .andReturn();

        String projectId = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");
        assertThat(timelineEventCount(projectId, "project", projectId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/projects/" + projectNo(projectId) + "/items")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-founder",
                                  "name": "首版任务看板",
                                  "description": "做 project item 看板和 claim 入口。",
                                  "deliveryStandard": "交付 item board 页面、item claim API、repo diff、预览图和任务状态说明。",
                                  "acceptanceCriteria": ["item board 页面可访问", "item claim API 可调用", "任务状态说明完整"],
                                  "difficultyScore": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.rewardPreviewShares").isNumber());

        var workspace = mockMvc.perform(get("/api/v1/posts/" + projectNo(projectId) + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shares.shareTotal").value(21000000))
                .andExpect(jsonPath("$.items[0].title").value("首版任务看板"))
                .andReturn();

        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");
        assertThat(timelineEventCount(projectId, "item", itemId)).isEqualTo(1);

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andReturn();

        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);
        String internalOrderId = internalOrderId(orderNo);
        assertThat(timelineEventCount(projectId, "order", internalOrderId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'wb-delivery-result-" + orderNo + "')]").exists());

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/progress")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submittedByAccountId": "acct-worker",
                                  "stepTitle": "完成首版",
                                  "summary": "已完成任务看板首版。",
                                  "links": [
                                    {"label": "Preview", "href": "https://example.com/project/board.png"}
                                  ],
                                  "progressPayload": {
                                    "runId": "project-run-001"
                                  },
                                  "executionMode": "AGENT",
                                  "agentRuntime": "worker-agent"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from project_timeline_events where project_id = ? and source_type = 'progress' and order_id = ?",
                Integer.class,
                projectId,
                internalOrderId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submittedByAccountId": "acct-worker",
                                  "summary": "agent 已完成任务看板",
                                   "links": [
                                     {"label": "Preview", "href": "https://example.com/project/board.png"}
                                   ],
                                   "criteriaRefs": [
                                     "item board 页面可访问"
                                   ],
                                   "proofPayload": {
                                     "runId": "project-run-001"
                                   },
                                  "executionMode": "AGENT",
                                  "agentRuntime": "worker-agent"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from project_timeline_events where project_id = ? and source_type = 'proof' and order_id = ?",
                Integer.class,
                projectId,
                internalOrderId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.itemId").value(itemId))
                .andExpect(jsonPath("$.order.status").value("delivered"))
                .andExpect(jsonPath("$.order.reservedShares").isNumber())
                .andExpect(jsonPath("$.displayPhase").value("waiting_lead_acceptance"));
    }

    @Test
    void projectOwnerCanClaimOwnItem() throws Exception {
        String projectId = publishProjectWithOneItem("Owner 自领项目", "Owner 自领任务");
        String itemId = firstItemId(projectId);

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-founder",
                                  "buyerNote": "owner 自己推进首个任务"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andReturn();

        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId)).with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.postKind").value("project"))
                .andExpect(jsonPath("$.order.buyerAccountId").value("acct-founder"))
                .andExpect(jsonPath("$.order.sellerAccountId").value("acct-founder"))
                .andExpect(jsonPath("$.order.fulfillerAccountId").value("acct-founder"))
                .andExpect(jsonPath("$.order.acceptorAccountId").value("acct-founder"))
                .andExpect(jsonPath("$.order.currentAccountRole").value("payer"))
                .andExpect(jsonPath("$.order.status").value("claimed"))
                .andExpect(jsonPath("$.order.reservedShares").isNumber());
    }

    @Test
    void publishProjectCanCreateInitialItems() throws Exception {
        // 中文注释：Project 外部协作证据使用轻量 Git 仓库 URL，闭环测试可用 git remote -v 复核。
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "自动研究公司",
                                  "description": "做一个自动研究公司并持续发布任务",
                                  "items": [
                                    {
                                      "name": "整理竞品清单",
                                      "description": "收集 10 个竞品和链接。",
                                      "deliveryStandard": "交付竞品表格和来源链接，至少 10 条有效记录。",
                                      "acceptanceCriteria": ["表格至少 10 条有效记录", "每条记录包含来源链接"],
                                      "difficultyScore": 1.5
                                    },
                                    {
                                      "name": "生成首页文案",
                                      "description": "输出一版首页文案。",
                                      "deliveryStandard": "交付 markdown 文案，包含 hero、痛点和 CTA。",
                                      "acceptanceCriteria": ["markdown 包含 hero", "markdown 包含痛点和 CTA"]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.referenceLinks[0]").value("http://localhost:3001/whenrealizing/monopolyfun"))
                .andExpect(jsonPath("$.project.maintenanceMode").value("repo_first"))
                .andExpect(jsonPath("$.project.repoProvider").value("forgejo"))
                .andExpect(jsonPath("$.project.repoOwner").value("whenrealizing"))
                .andExpect(jsonPath("$.project.repoName").value("monopolyfun"))
                .andExpect(jsonPath("$.project.defaultMaintenanceCommands[0]").value("git remote -v"))
                .andExpect(jsonPath("$.project.maintenancePlaybook.taskTypes[0]").value("backlog_triage"))
                .andExpect(jsonPath("$.project.projectNo").isNotEmpty())
                .andExpect(jsonPath("$.project.resourceKey").doesNotExist())
                .andExpect(jsonPath("$.project.capabilities").doesNotExist())
                .andExpect(jsonPath("$.project.blockedCapabilities").doesNotExist())
                .andReturn();

        String projectId = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");
        String projectNo = projectNo(projectId);

        mockMvc.perform(get("/api/v1/projects/" + projectNo)
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceKey").isNotEmpty())
                .andExpect(jsonPath("$.capabilities[0]").value("project.create_item"))
                .andExpect(jsonPath("$.capabilities[1]").doesNotExist());

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/items")
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-founder",
                                  "name": "补充 agent 验收",
                                  "deliveryStandard": "提交 agent 字段验收记录。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceKey").isNotEmpty());

        mockMvc.perform(get("/api/v1/posts/" + projectNo + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[*].title").value(hasItem("整理竞品清单")))
                .andExpect(jsonPath("$.items[?(@.title == '整理竞品清单')].fulfillmentMode").value(hasItem("reviewed_delivery")))
                .andExpect(jsonPath("$.items[?(@.title == '整理竞品清单')].deliveryMode").value(hasItem("reviewed_delivery")))
                .andExpect(jsonPath("$.items[?(@.title == '整理竞品清单')].deliverySource").value(hasItem("submitted_result")))
                .andExpect(jsonPath("$.items[0].resourceKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].capabilities").doesNotExist())
                .andExpect(jsonPath("$.items[0].blockedCapabilities").doesNotExist())
                .andExpect(jsonPath("$.items[?(@.title == '整理竞品清单')].difficultyScore").value(hasItem(1.5)))
                .andExpect(jsonPath("$.items[?(@.title == '整理竞品清单')].seatCount").value(hasItem(1)))
                .andExpect(jsonPath("$.items[*].title").value(hasItem("生成首页文案")));
    }

    @Test
    void publishProjectReturnsConfigErrorWhenManagedRepoProviderMissingConfig() throws Exception {
        when(repoProviderClient.provisionPublicRepository(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Forgejo repository service credentials are not configured"));
        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "缺少仓库项目",
                                  "description": "公司项目必须绑定仓库。",
                                  "items": [
                                    {
                                      "name": "初始化任务",
                                      "deliveryStandard": "提交计划。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("config.missing"));
    }

    @Test
    void updateProjectKeepsReferenceLinksRequired() throws Exception {
        String projectId = publishProjectWithOneItem("仓库更新项目", "初始化任务");
        mockMvc.perform(patch("/api/v1/projects/" + projectNo(projectId))
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-founder",
                                  "title": "仓库更新项目",
                                  "description": "更新项目时仍保留仓库事实。",
                                  "goal": "持续维护仓库。",
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void projectListKeepsRootOutOfChildProjectIndex() throws Exception {
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "索引过滤项目",
                                  "description": "验证 Root Project 和 Child Project 的列表边界。",
                                  "items": [
                                    {
                                      "name": "列表过滤验证",
                                      "deliveryStandard": "Root 只进入系统入口，Child 进入普通项目列表。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectLevel").value("child"))
                .andExpect(jsonPath("$.project.parentProjectId").value("project-root"))
                .andReturn();

        String projectId = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");
        String projectNo = projectNo(projectId);
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").doesNotExist())
                .andExpect(jsonPath("$.items[0].projectNo").value(projectNo))
                .andExpect(jsonPath("$.items[0].projectLevel").value("child"))
                .andExpect(jsonPath("$.pageInfo.hasMore").value(false));
        assertThat(jdbcTemplate.queryForObject("select count(*) from projects where project_level = 'root'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from projects where project_level = 'child' and parent_project_id = 'project-root'", Integer.class)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/posts/monopolyfun/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.id").doesNotExist())
                .andExpect(jsonPath("$.post.projectNo").value("monopolyfun"))
                .andExpect(jsonPath("$.shares.shareTotal").value(21000000));

        mockMvc.perform(get("/api/v1/projects/root"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.projectNo").value("monopolyfun"))
                .andExpect(jsonPath("$.projectLevel").value("root"));
    }

    @Test
    void registeredAccountCanPublishProjectWithoutRootCapability() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "开放发布项目",
                                  "description": "普通注册账号可以直接发布项目。",
                                  "items": [
                                    {
                                      "name": "开放发布",
                                      "deliveryStandard": "普通注册账号也可以直接发布项目。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectLevel").value("child"));
    }

    @Test
    void inactiveOwnerMovesToActiveTopShareHolder() throws Exception {
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Owner 移交项目",
                                  "description": "用 shares holder 继承 owner。",
                                  "items": [
                                    {
                                      "name": "完成可验证任务",
                                      "description": "让 worker 获得 shares。",
                                      "deliveryStandard": "提交可验证链接。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");
        String itemId = JsonTestSupport.readString(mockMvc.perform(get("/api/v1/posts/" + projectNo(projectId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "/items/0/id");
        String orderId = JsonTestSupport.readString(mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId": "acct-worker"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "/subjectId");
        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                   "submittedByAccountId": "acct-worker",
                                   "summary": "完成",
                                   "links": [{"label": "Proof", "href": "https://example.com/proof"}],
                                   "criteriaRefs": ["提交可验证链接。"],
                                   "executionMode": "HUMAN"
                                 }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptedByAccountId": "acct-founder", "note": "ok"}
                                """))
                .andExpect(status().isOk());

        jdbcTemplate.update("update orders set dispute_window_expires_at = ?::timestamptz where order_no = ?", "2000-01-01T00:00:00Z", orderNo(orderId));
        assertThat(orderCommandService.finalizeExpiredDisputeWindows()).isEqualTo(1);

        jdbcTemplate.update("""
                update projects
                set metadata = metadata || '{"ownerLastActionAt":"2000-01-01T00:00:00Z"}'::jsonb
                where id = ?
                """, projectId);
        projectLifecycleService.handoffInactiveOwners(Instant.now());

        mockMvc.perform(get("/api/v1/projects/" + projectNo(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerAccountId").doesNotExist())
                .andExpect(jsonPath("$.ownerHandle").value("@worker"))
                .andExpect(jsonPath("$.status").value("active"));
        assertThat(roleCount(projectId, "acct-worker", "system_ceo")).isZero();
        mockMvc.perform(post("/api/v1/projects/" + projectNo(projectId) + "/items")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "name": "新 owner 继续推进",
                                  "deliveryStandard": "提交下一步计划。",
                                  "itemType": "research",
                                  "mode": "reviewed_delivery"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void inactiveOwnerWithoutShareHolderMakesProjectClaimable() throws Exception {
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "无人维护项目",
                                  "description": "没有 holder 时允许用户主动接替。",
                                  "items": [
                                    {
                                      "name": "初始任务",
                                      "deliveryStandard": "提交计划。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");
        jdbcTemplate.update("""
                update projects
                set metadata = metadata || '{"ownerLastActionAt":"2000-01-01T00:00:00Z"}'::jsonb
                where id = ?
                """, projectId);

        projectLifecycleService.handoffInactiveOwners(Instant.now());

        mockMvc.perform(get("/api/v1/projects/" + projectNo(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("claimable"));
        mockMvc.perform(post("/api/v1/projects/" + projectNo(projectId) + "/owner-claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "reason": "我来继续维护",
                                  "plan": "补充下一批任务并处理验收"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerAccountId").value("acct-worker"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void projectShareReleaseAutoSettlesForOrdinaryProject() throws Exception {
        String projectId = publishProjectWithOneItem("自动结算项目", "自动结算任务");
        String itemId = firstItemId(projectId);

        String orderNo = claimAndDeliverItem(itemId);
        String orderId = internalOrderId(orderNo);
        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderNo) + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptedByAccountId": "acct-founder", "note": "ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_OPEN"));

        assertThat(ledgerCount(orderId)).isZero();
        jdbcTemplate.update("update orders set dispute_window_expires_at = ?::timestamptz where id = ?", "2000-01-01T00:00:00Z", orderId);
        assertThat(orderCommandService.finalizeExpiredDisputeWindows()).isEqualTo(1);

        String releaseId = jdbcTemplate.queryForObject("select id from share_release_requests where order_id = ?", String.class, orderId);
        assertThat(jdbcTemplate.queryForObject("select status::text from share_release_requests where id = ?", String.class, releaseId)).isEqualTo("skipped");

        mockMvc.perform(get("/api/v1/share-release-requests/pending/me").with(SecurityTestSupport.session(jdbcTemplate, "acct-cfo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        assertThat(ledgerCount(orderId)).isEqualTo(1);
    }

    @Test
    void projectShareSettlementHoldLocksBeforeFinalRelease() throws Exception {
        String projectId = publishProjectWithOneItem("托管 shares 项目", "托管任务");

        String orderNo = claimAndDeliverItem(firstItemId(projectId));
        String orderId = internalOrderId(orderNo);

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderNo) + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptedByAccountId": "acct-founder", "note": "ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_OPEN"));

        assertThat(jdbcTemplate.queryForObject("select status from share_settlement_holds where order_id = ?", String.class, orderId)).isEqualTo("locked");
        assertThat(jdbcTemplate.queryForObject("select lock_reason from share_settlement_holds where order_id = ?", String.class, orderId)).isEqualTo("acceptance_window");
        assertThat(ledgerCount(orderId)).isZero();

        String earlyReleaseId = jdbcTemplate.queryForObject("select id from share_release_requests where order_id = ?", String.class, orderId);
        mockMvc.perform(post("/api/v1/share-release-requests/" + earlyReleaseId + "/approve")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-cfo")))
                .andExpect(status().isConflict());
        assertThat(ledgerCount(orderId)).isZero();

        jdbcTemplate.update("update orders set dispute_window_expires_at = ?::timestamptz where id = ?", "2000-01-01T00:00:00Z", orderId);
        assertThat(orderCommandService.finalizeExpiredDisputeWindows()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select status::text from orders where id = ?", String.class, orderId)).isEqualTo("final_accepted");
        assertThat(jdbcTemplate.queryForObject("select status from share_settlement_holds where order_id = ?", String.class, orderId)).isEqualTo("released");
        assertThat(jdbcTemplate.queryForObject("select release_reason from share_settlement_holds where order_id = ?", String.class, orderId)).isEqualTo("window_expired");
        assertThat(ledgerCount(orderId)).isEqualTo(1);
    }

    @Test
    void projectShareReleaseSkipsVacantCeoAndCfoSlots() throws Exception {
        String projectId = publishProjectWithOneItem("空席审批项目", "空席跳过任务");
        String itemId = firstItemId(projectId);

        String orderNo = claimAndDeliverItem(itemId);
        String orderId = internalOrderId(orderNo);
        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderNo) + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptedByAccountId": "acct-founder", "note": "ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_OPEN"));

        jdbcTemplate.update("update orders set dispute_window_expires_at = ?::timestamptz where id = ?", "2000-01-01T00:00:00Z", orderId);
        assertThat(orderCommandService.finalizeExpiredDisputeWindows()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("select status::text from share_release_requests where order_id = ?", String.class, orderId)).isEqualTo("skipped");
        assertThat(jdbcTemplate.queryForObject("select status from share_settlement_holds where order_id = ?", String.class, orderId)).isEqualTo("released");
        assertThat(ledgerCount(orderId)).isEqualTo(1);
    }

    @Test
    void rootProjectItemReleaseUsesRootCeoAndCfoApproval() throws Exception {
        publishProjectWithOneItem("系统角色启动项目", "初始化系统 CEO");
        mockMvc.perform(post("/api/v1/projects/monopolyfun/roles/system_ceo/assign")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "acct-founder"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/monopolyfun/roles/system_cfo/assign")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "acct-cfo"}
                                """))
                .andExpect(status().isOk());

        var rootItem = mockMvc.perform(post("/api/v1/projects/monopolyfun/items")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-founder",
                                  "name": "整理系统资料",
                                  "deliveryStandard": "提交可验证链接。",
                                  "itemType": "ops",
                                  "mode": "reviewed_delivery"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(rootItem.getResponse().getContentAsString(), "/id");
        String orderNo = claimAndDeliverItem(itemId);
        String orderId = internalOrderId(orderNo);
        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderNo) + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptedByAccountId": "acct-founder", "note": "ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_OPEN"));

        jdbcTemplate.update("update orders set dispute_window_expires_at = ?::timestamptz where id = ?", "2000-01-01T00:00:00Z", orderId);
        assertThat(orderCommandService.finalizeExpiredDisputeWindows()).isEqualTo(1);

        String releaseId = jdbcTemplate.queryForObject("select id from share_release_requests where order_id = ?", String.class, orderId);
        assertThat(jdbcTemplate.queryForObject("select issuer_type::text from share_release_requests where id = ?", String.class, releaseId)).isEqualTo("project");
        assertThat(jdbcTemplate.queryForObject("select issuer_id from share_release_requests where id = ?", String.class, releaseId)).isEqualTo("project-root");
        assertThat(ledgerCount(orderId)).isZero();
        mockMvc.perform(post("/api/v1/share-release-requests/" + releaseId + "/approve")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-cfo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        assertThat(jdbcTemplate.queryForObject("select issuer_type::text from shares_ledger where order_id = ?", String.class, orderId)).isEqualTo("project");
        assertThat(jdbcTemplate.queryForObject("select issuer_id from shares_ledger where order_id = ?", String.class, orderId)).isEqualTo("project-root");
    }

    @Test
    void projectPublishRejectsUnknownProjectPricingFieldsAndItemPricing() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "自定义定价项目",
                                  "description": "尝试覆盖系统定价。",
                                  "customPricing": "owner 自定义 shares 规则",
                                  "items": [
                                    {
                                      "name": "初始任务",
                                      "deliveryStandard": "提交计划。",
                                      "quantity": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "自定义 item 价格项目",
                                  "description": "尝试给 item 设置价格。",
                                  "items": [
                                    {
                                      "name": "初始任务",
                                      "deliveryStandard": "提交计划。",
                                      "quantity": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "自定义 item 价格项目",
                                  "description": "尝试给 item 设置价格。",
                                  "items": [
                                    {
                                      "name": "初始任务",
                                      "deliveryStandard": "提交计划。",
                                      "amount": 5,
                                      "difficultyScore": 1.2,
                                      "agentInstruction": "按 owner 指令执行"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishProjectRequiresInitialItemsInBackendAndOpenApi() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "缺少初始任务项目",
                                  "description": "验证 project 创建必须带初始任务。"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "空初始任务项目",
                                  "description": "验证空 items 被接口拒绝。",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest());

        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "直接初始化任务项目",
                                  "description": "创建项目时同步初始化首个任务。",
                                  "items": [
                                    {
                                      "name": "初始化任务",
                                      "deliveryStandard": "提交初始化结果。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectNo = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/projectNo");

        mockMvc.perform(get("/api/v1/posts/" + projectNo + "/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("初始化任务"))
                .andExpect(jsonPath("$[0].status").value("open"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PublishProjectRequest.required", hasItem("items")))
                .andExpect(jsonPath("$.components.schemas.PublishProjectRequest.properties.items.minItems").value(1))
                .andExpect(jsonPath("$.components.schemas.PublishProjectRequest.properties.rewardModel").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PublishProjectItemRequest.required", hasItem("name")))
                .andExpect(jsonPath("$.components.schemas.PublishProjectItemRequest.required", hasItem("deliveryStandard")));
    }

    @Test
    void projectRoleInviteEntersWorkbenchAndAcceptCreatesRoleTask() throws Exception {
        publishProjectWithOneItem("系统角色启动项目", "初始化系统授权");
        String projectNo = "monopolyfun";

        mockMvc.perform(post("/api/v1/projects/" + projectNo + "/roles/system_cto/invite")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-worker",
                                  "message": "请接手技术交付检查。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectNo").value(projectNo))
                .andExpect(jsonPath("$.roleCode").value("system_cto"))
                .andExpect(jsonPath("$.accountId").value("acct-worker"))
                .andExpect(jsonPath("$.status").value("ready"));

        var inviteWorkbench = mockMvc.perform(get("/api/v1/workbench")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("project_role_invite"))
                .andExpect(jsonPath("$[0].requiredRoleCode").value("system_cto"))
                .andExpect(jsonPath("$[0].actions[?(@.id == 'accept_project_invite')]").exists())
                .andReturn();
        String inviteItemNo = JsonTestSupport.readString(inviteWorkbench.getResponse().getContentAsString(), "/0/id");

        mockMvc.perform(post("/api/v1/workbench/" + inviteItemNo + "/project-invite/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.payload.projectNo").value(projectNo))
                .andExpect(jsonPath("$.payload.roleCode").value("system_cto"));

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/authority/me")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleCodes", hasItem("system_cto")))
                .andExpect(jsonPath("$.capabilities", hasItem("project.tech.manage")));

        mockMvc.perform(get("/api/v1/workbench")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("project_role_task"))
                .andExpect(jsonPath("$[0].requiredRoleCode").value("system_cto"))
                .andExpect(jsonPath("$[0].acceptanceCriteria", hasItem("检查 repo / PR / workflow 状态")));
    }

    @Test
    void publicProjectReadRedactsRoleAssignmentsAndGovernanceReadsNeedCapability() throws Exception {
        String projectId = publishProjectWithOneItem("权限脱敏项目", "初始化任务");
        String projectNo = projectNo(projectId);

        mockMvc.perform(get("/api/v1/projects/" + projectNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.ownerAccountId").doesNotExist())
                .andExpect(jsonPath("$.ownerHandle").value("@founder"))
                .andExpect(jsonPath("$.roles").isEmpty());

        mockMvc.perform(get("/api/v1/posts/" + projectNo + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.id").doesNotExist())
                .andExpect(jsonPath("$.post.ownerAccountId").doesNotExist())
                .andExpect(jsonPath("$.post.ownerHandle").value("@founder"))
                .andExpect(jsonPath("$.post.roles").isEmpty());

        mockMvc.perform(get("/api/v1/projects/" + projectNo + "/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

    }

    @Test
    void projectCodeProofRequiresPrSecurityEvidenceAndBoundArtifact() throws Exception {
        String projectId = publishProjectWithOneItem("PR 安全项目", "代码交付任务");
        String itemId = firstItemId(projectId);
        String orderId = JsonTestSupport.readString(mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId": "acct-worker"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "/subjectId");

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submittedByAccountId": "acct-worker",
                                  "summary": "提交 Forgejo PR 交付。",
                                  "links": [{"label": "PR", "href": "http://localhost:3001/whenrealizing/monopolyfun/pulls/42"}],
                                  "criteriaRefs": ["提交可验证链接。"],
                                  "proofPayload": {
                                    "deliveryType": "code_pr"
                                  },
                                  "executionMode": "AGENT"
                                }
                                """))
                .andExpect(status().isBadRequest());

        insertProofAsset(orderId, "asset-code-pr");

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submittedByAccountId": "acct-worker",
                                  "summary": "提交 Forgejo PR、CI 和安全策略证据。",
                                  "links": [{"label": "PR", "href": "http://localhost:3001/whenrealizing/monopolyfun/pulls/42"}],
                                  "artifacts": ["asset://asset-code-pr"],
                                  "criteriaRefs": ["提交可验证链接。"],
                                  "proofPayload": {
                                    "deliveryType": "code_pr",
                                    "prSecurity": {
                                      "orderNo": "%s",
                                      "repoUrl": "http://localhost:3001/whenrealizing/monopolyfun",
                                      "prUrl": "http://localhost:3001/whenrealizing/monopolyfun/pulls/42",
                                      "commitSha": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                      "ciStatus": "success",
                                      "securityPolicyResult": "passed",
                                      "maliciousFindings": []
                                    }
                                  },
                                  "executionMode": "AGENT"
                                }
                                """.formatted(orderNo(orderId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    private String publishProjectWithOneItem(String title, String itemName) throws Exception {
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "用于 shares release 审批测试。",
                                  "items": [
                                    {
                                      "name": "%s",
                                      "deliveryStandard": "提交可验证链接。"
                                    }
                                  ]
                                }
                                """.formatted(title, itemName)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");
    }

    private String firstItemId(String postId) throws Exception {
        return JsonTestSupport.readString(mockMvc.perform(get("/api/v1/posts/" + projectNo(postId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "/items/0/id");
    }

    private String claimAndDeliverItem(String itemId) throws Exception {
        String orderId = JsonTestSupport.readString(mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorAccountId": "acct-worker"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "/subjectId");
        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                   "submittedByAccountId": "acct-worker",
                                   "summary": "完成",
                                   "links": [{"label": "Proof", "href": "https://example.com/proof"}],
                                   "criteriaRefs": ["提交可验证链接。"],
                                   "executionMode": "HUMAN"
                                 }
                                """))
                .andExpect(status().isOk());
        return orderId;
    }

    private void insertProofAsset(String orderId, String assetId) {
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
                internalOrderId(orderNo(orderId)),
                "asset://" + assetId,
                "orders/" + orderNo(orderId) + "/patch.diff");
    }

    private int ledgerCount(String orderId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from shares_ledger where order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    private int roleCount(String projectId, String accountId, String roleCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from project_roles where project_id = ? and account_id = ? and role_code = ?::project_role_code",
                Integer.class,
                projectId,
                accountId,
                roleCode);
        return count == null ? 0 : count;
    }

    private int timelineEventCount(String projectId, String sourceType, String sourceId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from project_timeline_events where project_id = ? and source_type = ? and source_id = ?",
                Integer.class,
                projectId,
                sourceType,
                sourceId);
        return count == null ? 0 : count;
    }

    private String projectNo(String projectId) {
        return jdbcTemplate.queryForObject("select project_no from projects where id = ?", String.class, projectId);
    }

    private String orderNo(String orderId) {
        return jdbcTemplate.queryForObject("select order_no from orders where id = ? or order_no = ?", String.class, orderId, orderId);
    }

    private String internalOrderId(String orderNo) {
        return jdbcTemplate.queryForObject("select id from orders where order_no = ?", String.class, orderNo);
    }

    @Test
    void projectItemCreateRejectsCustomPricingFields() throws Exception {
        var projectCreate = mockMvc.perform(post("/api/v1/projects")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "后续任务定价项目",
                                  "description": "后续 item 也走系统定价。",
                                  "items": [
                                    {
                                      "name": "初始任务",
                                      "deliveryStandard": "提交计划。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String projectId = JsonTestSupport.readString(projectCreate.getResponse().getContentAsString(), "/project/id");

        mockMvc.perform(post("/api/v1/projects/" + projectNo(projectId) + "/items")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-founder"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-founder",
                                  "name": "错误自定义任务",
                                  "deliveryStandard": "提交下一步计划。",
                                  "amount": 2,
                                  "difficultyScore": 1.2,
                                  "agentInstruction": "按 owner 指令执行"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
