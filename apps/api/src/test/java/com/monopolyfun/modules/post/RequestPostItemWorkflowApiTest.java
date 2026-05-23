package com.monopolyfun;

import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.order.service.command.OrderCommandService;
import com.monopolyfun.modules.work.service.OrderWorkItemPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RequestPostItemWorkflowApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderWorkItemPublisher orderWorkItemPublisher;

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("truncate table business_id_sequences, share_release_requests, project_roles, organization_events, order_progress_updates, offers, requests, projects, order_events, shares_ledger, proofs, orders, listings, markets, accounts cascade");
        jdbcTemplate.update("""
                insert into accounts (id, handle, display_name, metadata)
                values
                ('acct-requester', '@requester', 'Requester', '{}'::jsonb),
                ('acct-worker', '@worker', 'Worker', '{}'::jsonb),
                ('acct-stranger', '@stranger', 'Stranger', '{}'::jsonb)
                """);
    }

    @Test
    void publishRequestCreatesIndependentItemsAndWorkerAgentDelivery() throws Exception {
        var requestCreate = mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "找人整理虚拟资料",
                                  "description": "按不同 item 收集和整理资料。",
                                   "currency": "USD",
                                   "paymentMethod": "okx_direct_pay",
                                  "items": [
                                    {
                                      "name": "整理 10 个竞品",
                                      "description": "输出结构化表格。",
                                      "deliveryStandard": "提交包含 10 个竞品、价格和来源链接的表格。",
                                      "acceptanceCriteria": ["表格包含 10 个竞品", "每条记录包含来源链接"],
                                      "amount": 100,
                                      "quantity": 2,
                                      "agentInstruction": "按备注行业整理竞品表格。"
                                    },
                                    {
                                      "name": "整理 5 个渠道",
                                      "description": "输出渠道清单。",
                                      "deliveryStandard": "提交包含 5 个渠道、入口和联系方式的表格。",
                                      "acceptanceCriteria": ["表格包含 5 个渠道", "每条渠道包含入口和联系方式"],
                                      "amount": 60,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.resourceKey").doesNotExist())
                .andExpect(jsonPath("$.request.capabilities").doesNotExist())
                .andExpect(jsonPath("$.request.blockedCapabilities").doesNotExist())
                .andReturn();

        String requestId = JsonTestSupport.readString(requestCreate.getResponse().getContentAsString(), "/request/id");
        String requestNo = requestNo(requestId);

        mockMvc.perform(get("/api/v1/requests/" + requestNo)
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-requester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceKey").isNotEmpty())
                .andExpect(jsonPath("$.capabilities[0]").value("request.create_item"))
                .andExpect(jsonPath("$.capabilities[1]").value("request.close"));

        var workspace = mockMvc.perform(get("/api/v1/posts/" + requestNo + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("整理 10 个竞品"))
                .andExpect(jsonPath("$.items[0].budgetAmount").value(100))
                .andExpect(jsonPath("$.items[0].seatCount").value(2))
                .andExpect(jsonPath("$.items[0].resourceKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].capabilities").doesNotExist())
                .andExpect(jsonPath("$.items[0].blockedCapabilities").doesNotExist())
                .andExpect(jsonPath("$.items[0].deliveryMode").value("reviewed_delivery"))
                .andExpect(jsonPath("$.items[0].deliverySource").value("submitted_result"))
                .andExpect(jsonPath("$.items[1].title").value("整理 5 个渠道"))
                .andExpect(jsonPath("$.items[1].budgetAmount").value(60))
                .andReturn();

        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        mockMvc.perform(get("/api/v1/posts/" + requestNo + "/workspace")
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resourceKey").isNotEmpty())
                .andExpect(jsonPath("$.items[0].capabilities[0]").value("post_item.claim"));

        mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-requester"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "buyerNote": "缺少执行人钱包"
                                }
                                """))
                .andExpect(status().isBadRequest());

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "buyerNote": "AI 图像工具方向",
                                  "paymentRecipient": "0x2222222222222222222222222222222222222222"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andExpect(jsonPath("$.payload.paymentRequired").value(false))
                .andExpect(jsonPath("$.payload.paymentActorAccountId").value("acct-requester"))
                .andExpect(jsonPath("$.payload.paymentRecipient").value("0x2222222222222222222222222222222222222222"))
                .andReturn();

        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);
        // 中文注释：命令回执允许返回公开订单号，测试先解析真实 id 再读取仓储实体。
        assertThat(orderRepository.findById(orderId(orderId)).orElseThrow().metadata()).doesNotContainKey("paymentDueAt");

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-requester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'wb-money-payment-" + orderNo + "')]").exists());

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "summary": "agent 已完成竞品表格"
                                }
                                """))
                .andExpect(status().isNotFound());

        markOrderPaymentAuthorized(orderId);

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'wb-delivery-result-" + orderNo + "')]").doesNotExist());

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "summary": "authorized 状态恶意交付"
                                }
                                """))
                .andExpect(status().isNotFound());

        markOrderPaymentCaptured(orderId);

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-worker")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'wb-delivery-result-" + orderNo + "')]").exists());

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-stranger"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submittedByAccountId": "acct-stranger",
                                  "summary": "外部账号恶意提交 proof",
                                  "executionMode": "AGENT",
                                  "links": [{"label": "proof", "href": "https://example.com/proof"}],
                                  "criteriaRefs": ["表格包含 10 个竞品"]
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "summary": "agent 已完成竞品表格",
                                  "output": {
                                    "url": "https://example.com/request/competitors.csv"
                                  },
                                  "sourceReceipt": {
                                    "runId": "request-run-001"
                                  },
                                  "agentRuntime": "worker-agent"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-requester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.order.postKind").value("request"))
                .andExpect(jsonPath("$.order.buyerAccountId").value("acct-requester"))
                .andExpect(jsonPath("$.order.sellerAccountId").value("acct-worker"))
                .andExpect(jsonPath("$.order.fulfillerAccountId").value("acct-worker"))
                .andExpect(jsonPath("$.order.acceptorAccountId").value("acct-requester"))
                .andExpect(jsonPath("$.order.paymentMethod").value("okx_direct_pay"))
                .andExpect(jsonPath("$.order.currentAccountRole").value("payer"))
                .andExpect(jsonPath("$.order.status").value("delivered"))
                .andExpect(jsonPath("$.order.budgetAmount").doesNotExist())
                .andExpect(jsonPath("$.order.deliveryPayload.url").value("https://example.com/request/competitors.csv"));

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acceptedByAccountId": "acct-requester",
                                  "note": "验收通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_OPEN"));

        jdbcTemplate.update("update orders set dispute_window_expires_at = ?::timestamptz where order_no = ?", "2000-01-01T00:00:00Z", orderNo);
        assertThat(orderCommandService.finalizeExpiredDisputeWindows()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-requester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("final_accepted"));
    }

    @Test
    void publishRequestAcceptsCentDecimalBudget() throws Exception {
        var requestCreate = mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "找人做一次低额验证",
                                  "description": "验证公开需求支持小数预算。",
                                  "currency": "USD",
                                  "paymentMethod": "okx_direct_pay",
                                  "items": [
                                    {
                                      "name": "小数预算任务",
                                      "description": "输出验证结果。",
                                      "deliveryStandard": "提交可验证的结果链接。",
                                      "acceptanceCriteria": ["结果链接可打开"],
                                      "amount": 0.01,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = JsonTestSupport.readString(requestCreate.getResponse().getContentAsString(), "/request/id");

        var workspace = mockMvc.perform(get("/api/v1/posts/" + requestNo(requestId) + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].budgetAmount").value(0.01))
                .andReturn();

        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "paymentRecipient": "0x2222222222222222222222222222222222222222"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");

        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId)).with(SecurityTestSupport.session(jdbcTemplate, "acct-requester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.settlementAmount").value(0.01));
    }

    @Test
    void requestPublishRejectsSharesPaymentMethod() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "错误支付方式需求",
                                  "description": "公开需求需要买方 OKX 付款。",
                                  "currency": "USD",
                                  "paymentMethod": "shares",
                                  "items": [
                                    {
                                      "name": "不可发布需求",
                                      "description": "Shares 支付不能用于公开需求。",
                                      "deliveryStandard": "不会创建订单。",
                                      "acceptanceCriteria": ["不会创建订单"],
                                      "amount": 10,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lastRequestItemClaimClosesPostStatus() throws Exception {
        var requestCreate = mockMvc.perform(post("/api/v1/requests")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-requester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "找人处理一次性任务",
                                  "description": "只有一个执行名额。",
                                   "currency": "USD",
                                   "paymentMethod": "okx_direct_pay",
                                  "items": [
                                    {
                                      "name": "整理资料",
                                      "description": "输出一份资料。",
                                      "deliveryStandard": "提交资料链接。",
                                      "acceptanceCriteria": ["资料链接可打开"],
                                      "amount": 10,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = JsonTestSupport.readString(requestCreate.getResponse().getContentAsString(), "/request/id");
        var workspace = mockMvc.perform(get("/api/v1/posts/" + requestNo(requestId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-worker"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-worker",
                                  "paymentRecipient": "0x2222222222222222222222222222222222222222"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"));

        mockMvc.perform(get("/api/v1/posts/" + requestNo(requestId) + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("closed"))
                .andExpect(jsonPath("$.post.tradeStatus").value("matched"))
                .andExpect(jsonPath("$.post.visibility").value("participant_only"))
                .andExpect(jsonPath("$.post.stockFilled").value(1))
                .andExpect(jsonPath("$.items[0].status").value("in_progress"))
                .andExpect(jsonPath("$.items[0].activeOrderNo").isNotEmpty())
                .andExpect(jsonPath("$.items[0].activeOrderPaymentRequired").value(true))
                .andExpect(jsonPath("$.items[0].activeOrderPaymentStatus").value("missing_payment_intent"));
    }

    private String requestNo(String requestId) {
        return jdbcTemplate.queryForObject("select request_no from requests where id = ?", String.class, requestId);
    }

    private String orderNo(String orderId) {
        return jdbcTemplate.queryForObject("select order_no from orders where id = ? or order_no = ?", String.class, orderId, orderId);
    }

    private String orderId(String orderIdOrNo) {
        return jdbcTemplate.queryForObject("select id from orders where id = ? or order_no = ?", String.class, orderIdOrNo, orderIdOrNo);
    }

    private void markOrderPaymentCaptured(String orderIdOrNo) {
        String orderId = jdbcTemplate.queryForObject("select id from orders where id = ? or order_no = ?", String.class, orderIdOrNo, orderIdOrNo);
        jdbcTemplate.update("""
                        insert into payment_intents (
                          id, payment_no, order_id, account_id, provider, provider_payment_ref, status, amount_minor,
                          currency, callback_token, captured_at, metadata, created_at, updated_at
                        ) values (?, ?, ?, 'acct-requester', 'fake', ?, 'captured'::payment_intent_status, 10000,
                          'USD', ?, now(), '{"paymentMethod":"okx_direct_pay"}'::jsonb, now(), now())
                        on conflict (order_id) do update set
                          provider_payment_ref = excluded.provider_payment_ref,
                          status = 'captured'::payment_intent_status,
                          captured_at = now(),
                          metadata = excluded.metadata,
                          updated_at = now()
                        """,
                "payment-" + orderId,
                "PAY-" + orderId,
                orderId,
                "provider-" + orderId,
                "token-" + orderId);
        orderWorkItemPublisher.publishPaymentCaptured(orderRepository.findById(orderId).orElseThrow(), Instant.now());
    }

    private void markOrderPaymentAuthorized(String orderIdOrNo) {
        String orderId = jdbcTemplate.queryForObject("select id from orders where id = ? or order_no = ?", String.class, orderIdOrNo, orderIdOrNo);
        jdbcTemplate.update("""
                        insert into payment_intents (
                          id, payment_no, order_id, account_id, provider, provider_payment_ref, status, amount_minor,
                          currency, callback_token, authorized_at, metadata, created_at, updated_at
                        ) values (?, ?, ?, 'acct-requester', 'fake', ?, 'authorized'::payment_intent_status, 10000,
                          'USD', ?, now(), '{"paymentMethod":"okx_direct_pay"}'::jsonb, now(), now())
                        on conflict (order_id) do update set
                          provider_payment_ref = excluded.provider_payment_ref,
                          status = 'authorized'::payment_intent_status,
                          authorized_at = now(),
                          metadata = excluded.metadata,
                          updated_at = now()
                        """,
                "payment-" + orderId,
                "PAY-" + orderId,
                orderId,
                "provider-" + orderId,
                "token-" + orderId);
    }
}
