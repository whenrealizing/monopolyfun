package com.monopolyfun;

import com.monopolyfun.modules.order.infra.OrderRepository;
import com.monopolyfun.modules.project.domain.ProjectRoleCode;
import com.monopolyfun.modules.project.infra.ProjectRoleRepository;
import com.monopolyfun.modules.project.service.RootProjectService;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OfferPostItemWorkflowApiTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RootProjectService rootProjectService;

    @Autowired
    private ProjectRoleRepository projectRoleRepository;

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
                 ('acct-seller', '@seller', 'Seller', '{}'::jsonb),
                 ('acct-buyer', '@buyer', 'Buyer', '{}'::jsonb),
                 ('acct-reviewer', '@reviewer', 'Reviewer', '{}'::jsonb),
                 ('acct-member', '@member', 'Member', '{}'::jsonb),
                 ('acct-outsider', '@outsider', 'Outsider', '{}'::jsonb)
                """);
        rootProjectService.ensureRootProject("acct-seller");
        projectRoleRepository.assignRole(RootProjectService.ROOT_PROJECT_ID, ProjectRoleCode.SYSTEM_CTO, "acct-reviewer", "acct-seller");
    }

    @Test
    void publishOfferCreatesItemsAndItemClaimCreatesOrder() throws Exception {
        var offerCreate = mockMvc.perform(post("/api/v1/offers")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "二手数码店",
                                   "description": "卖多种二手手机和配件。",
                                   "currency": "USD",
                                   "paymentMethod": "okx_direct_pay",
                                   "paymentRecipient": "0x1111111111111111111111111111111111111111",
                                   "items": [
                                    {
                                      "name": "iPhone 15 Pro",
                                      "description": "单独上架一台苹果手机。",
                                      "deliveryStandard": "交付手机、验机视频和快递单号。",
                                      "acceptanceCriteria": ["快递单号可查询", "验机视频可打开"],
                                      "amount": 5200,
                                      "quantity": 2,
                                      "agentInstruction": "按买家备注生成交付说明并上传验机材料。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer.id").isNotEmpty())
                .andExpect(jsonPath("$.offer.resourceKey").doesNotExist())
                .andExpect(jsonPath("$.offer.capabilities").doesNotExist())
                .andExpect(jsonPath("$.offer.blockedCapabilities").doesNotExist())
                .andExpect(jsonPath("$.receipt.type").value("publish_offer"))
                .andExpect(jsonPath("$.receipt.status").value("offer_created"))
                .andReturn();

        String offerId = JsonTestSupport.readString(offerCreate.getResponse().getContentAsString(), "/offer/id");
        String offerNo = offerNo(offerId);

        mockMvc.perform(get("/api/v1/offers/" + offerNo)
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceKey").isNotEmpty())
                .andExpect(jsonPath("$.capabilities[0]").value("offer.create_item"))
                .andExpect(jsonPath("$.capabilities[1]").value("offer.close"));

        var workspace = mockMvc.perform(get("/api/v1/posts/" + offerNo + "/workspace")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("iPhone 15 Pro"))
                .andExpect(jsonPath("$.items[0].status").value("open"))
                .andExpect(jsonPath("$.items[0].resourceKey").doesNotExist())
                .andExpect(jsonPath("$.items[0].capabilities").doesNotExist())
                .andExpect(jsonPath("$.items[0].blockedCapabilities").doesNotExist())
                .andExpect(jsonPath("$.items[0].deliveryMode").value("reviewed_delivery"))
                .andExpect(jsonPath("$.items[0].deliverySource").value("submitted_result"))
                .andExpect(jsonPath("$.items[0].priceAmount").value(5200))
                .andExpect(jsonPath("$.items[0].seatCount").value(2))
                .andReturn();

        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from post_items_read_model where item_id = ? and listing_status = 'open'",
                Integer.class,
                itemId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/posts/" + offerNo + "/workspace")
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resourceKey").isNotEmpty())
                .andExpect(jsonPath("$.items[0].capabilities[0]").value("post_item.claim"));

        mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/posts/" + offerNo + "/items")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "name": "Samsung S24",
                                  "description": "单独上架一台三星手机。",
                                  "deliveryStandard": "交付手机、验机视频和快递单号。",
                                  "acceptanceCriteria": ["快递单号可查询", "验机视频可打开"],
                                  "amount": 4800,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Samsung S24"));

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andExpect(jsonPath("$.payload.paymentRequired").value(true))
                .andExpect(jsonPath("$.payload.paymentActorAccountId").value("acct-buyer"))
                .andExpect(jsonPath("$.payload.paymentIntentId").isNotEmpty())
                .andExpect(jsonPath("$.payload.paymentIntentStatus").value("PENDING"))
                .andReturn();

        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String paymentIntentId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/payload/paymentIntentId");
        assertThat(jdbcTemplate.queryForObject(
                "select latest_order_id from post_items_read_model where item_id = ?",
                String.class,
                itemId)).isEqualTo(orderId(orderId));
        assertThat(jdbcTemplate.queryForObject(
                "select latest_payment_intent_id from order_payment_state where order_id = ?",
                String.class,
                orderId(orderId))).isEqualTo(paymentIntentId);

        mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/payments/intents/" + paymentIntentId)
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceKey").doesNotExist())
                .andExpect(jsonPath("$.capabilities").doesNotExist())
                .andExpect(jsonPath("$.blockedCapabilities").doesNotExist());

        mockMvc.perform(get("/api/v1/payments/intents/" + paymentIntentId)
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceKey").isNotEmpty())
                .andExpect(jsonPath("$.capabilities[0]").value("payment.refresh"))
                .andExpect(jsonPath("$.capabilities[1]").value("payment.cancel"));

        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId)).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.order.postKind").value("offer"))
                .andExpect(jsonPath("$.order.buyerAccountId").value("acct-buyer"))
                .andExpect(jsonPath("$.order.sellerAccountId").value("acct-seller"))
                .andExpect(jsonPath("$.order.fulfillerAccountId").value("acct-seller"))
                .andExpect(jsonPath("$.order.acceptorAccountId").value("acct-buyer"))
                .andExpect(jsonPath("$.order.currentAccountRole").value("payer"))
                .andExpect(jsonPath("$.order.resourceKey").doesNotExist())
                .andExpect(jsonPath("$.order.capabilities").doesNotExist())
                .andExpect(jsonPath("$.order.blockedCapabilities").doesNotExist())
                .andExpect(jsonPath("$.post.kind").value("offer"))
                .andExpect(jsonPath("$.post.id").value(offerId));

        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId))
                        .param("includeAgent", "true")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.resourceKey").isNotEmpty())
                .andExpect(jsonPath("$.order.capabilities[0]").value("order.pay"))
                .andExpect(jsonPath("$.post.kind").value("offer"))
                .andExpect(jsonPath("$.post.id").value(offerId));
    }

    @Test
    void lastOfferItemClaimClosesPostStatus() throws Exception {
        var offerCreate = mockMvc.perform(post("/api/v1/offers")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "限量数字商品",
                                  "description": "只有一份库存。",
                                  "currency": "USD",
                                  "paymentMethod": "okx_direct_pay",
                                  "paymentRecipient": "0x1111111111111111111111111111111111111111",
                                  "items": [
                                    {
                                      "name": "唯一下载码",
                                      "description": "交付一份下载码。",
                                      "deliveryStandard": "交付可验证下载码。",
                                      "acceptanceCriteria": ["下载码可验证"],
                                      "amount": 99,
                                      "quantity": 1
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String offerId = JsonTestSupport.readString(offerCreate.getResponse().getContentAsString(), "/offer/id");
        var workspace = mockMvc.perform(get("/api/v1/posts/" + offerNo(offerId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"));

        mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-member"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/posts/" + offerNo(offerId) + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.post.status").value("closed"))
                .andExpect(jsonPath("$.post.tradeStatus").value("sold_out"))
                .andExpect(jsonPath("$.post.visibility").value("participant_only"))
                .andExpect(jsonPath("$.post.stockSold").value(1));
    }

    @Test
    void offerPublishRejectsSharesPaymentMethod() throws Exception {
        mockMvc.perform(post("/api/v1/offers")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "错误支付方式商品",
                                  "description": "买卖商品必须走 OKX 支付。",
                                  "currency": "USD",
                                  "paymentMethod": "shares",
                                  "items": [
                                    {
                                      "name": "不可发布商品",
                                      "description": "Shares 支付不能用于买卖商品。",
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
    void publishOfferCanCreateMultipleItemsInOneRequest() throws Exception {
        var offerCreate = mockMvc.perform(post("/api/v1/offers")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "虚拟商品店",
                                  "description": "一次发布多个虚拟商品 item。",
                                   "currency": "USD",
                                   "paymentMethod": "okx_direct_pay",
                                   "paymentRecipient": "0x1111111111111111111111111111111111111111",
                                  "items": [
                                    {
                                      "name": "GPT 头像图",
                                      "description": "生成一张头像图。",
                                      "deliveryStandard": "交付图片链接，链接可打开且图片符合备注。",
                                      "acceptanceCriteria": ["图片链接可打开", "图片内容符合备注"],
                                      "amount": 12,
                                      "quantity": 10
                                    },
                                    {
                                      "name": "资料整理 Agent",
                                      "description": "按备注整理一个资料包。",
                                      "deliveryStandard": "交付资料包链接，链接可打开并可下载。",
                                      "acceptanceCriteria": ["资料包链接可打开", "资料包可以下载"],
                                      "amount": 24,
                                      "quantity": 3,
                                      "agentInstruction": "按买家备注整理资料包并上传链接。"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String offerId = JsonTestSupport.readString(offerCreate.getResponse().getContentAsString(), "/offer/id");

        mockMvc.perform(get("/api/v1/posts/" + offerNo(offerId) + "/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("GPT 头像图"))
                .andExpect(jsonPath("$.items[0].priceAmount").value(12))
                .andExpect(jsonPath("$.items[0].seatCount").value(10))
                .andExpect(jsonPath("$.items[0].deliverySource").value("submitted_result"))
                .andExpect(jsonPath("$.items[1].title").value("资料整理 Agent"))
                .andExpect(jsonPath("$.items[1].priceAmount").value(24))
                .andExpect(jsonPath("$.items[1].seatCount").value(3))
                .andExpect(jsonPath("$.items[1].deliveryMode").value("reviewed_delivery"))
                .andExpect(jsonPath("$.items[1].deliverySource").value("submitted_result"));
    }

    @Test
    void userAgentDeliveryResultUsesWorkbenchThenCompletesOrder() throws Exception {
        String offerId = createOffer("AI 图片店");

        var createdItem = mockMvc.perform(post("/api/v1/posts/" + offerNo(offerId) + "/items")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "name": "GPT 海报图",
                                  "description": "本地 agent 生成一张海报图。",
                                  "deliveryStandard": "交付图片链接和生成摘要，图片可打开且内容匹配买家备注。",
                                  "acceptanceCriteria": ["图片链接可打开", "内容匹配买家备注"],
                                  "amount": 99,
                                  "quantity": 1,
                                  "agentInstruction": "使用图片生成 agent 生成海报图并上传结果。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryMode").value("reviewed_delivery"))
                .andExpect(jsonPath("$.deliverySource").value("submitted_result"))
                .andReturn();

        String itemId = JsonTestSupport.readString(createdItem.getResponse().getContentAsString(), "/id");
        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "buyerNote": "黑底未来感猫咪海报"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andReturn();

        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("complete_money_payment")));

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-seller")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("delivery_result_due"))));

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "summary": "付款前恶意交付"
                                }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/proofs")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submittedByAccountId": "acct-seller",
                                  "summary": "付款前恶意 proof",
                                  "executionMode": "AGENT",
                                  "links": [{"label": "proof", "href": "https://example.com/proof"}],
                                  "criteriaRefs": ["付款后交付"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment.money_settlement.capture_required"));

        markOrderPaymentCaptured(orderId);

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-seller")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("delivery_result_due")))
                .andExpect(content().string(containsString(orderNo)));

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("delivery_result_due"))));

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "summary": "agent 已生成海报图",
                                  "output": {
                                    "url": "https://example.com/result/cat.png"
                                  },
                                  "sourceReceipt": {
                                    "runId": "run-001"
                                  },
                                  "agentRuntime": "runtime-seller-mac"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-outsider"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acceptedByAccountId": "acct-outsider",
                                  "note": "外部账号恶意验收"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId)).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("delivered"))
                .andExpect(jsonPath("$.order.deliveryPayload.url").value("https://example.com/result/cat.png"))
                .andExpect(jsonPath("$.order.deliveryReceipt.runId").value("run-001"))
                .andExpect(jsonPath("$.order.agentRuntimeId").value("runtime-seller-mac"));
    }

    @Test
    void disputeOpenerCanCancelDisputeThenAcceptDelivery() throws Exception {
        String offerId = createOffer("AI 争议撤回素材");
        var workspace = mockMvc.perform(get("/api/v1/posts/" + offerNo(offerId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "buyerNote": "交付一份可下载素材"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);
        markOrderPaymentCaptured(orderId);

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "summary": "agent 交付素材链接",
                                  "output": {
                                    "url": "https://example.com/assets.zip"
                                  },
                                  "sourceReceipt": {
                                    "runId": "run-cancel-dispute-001"
                                  },
                                  "agentRuntime": "runtime-seller-mac"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        var disputeReceipt = mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/dispute")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "reason": "先发起争议确认素材内容",
                                  "evidenceRefs": ["https://example.com/evidence/checking"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"))
                .andReturn();
        String reviewItemNo = JsonTestSupport.readString(disputeReceipt.getResponse().getContentAsString(), "/payload/reviewItemNo");

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("disputed"))
                .andExpect(jsonPath("$.order.disputeOpenedByAccountId").value("acct-buyer"))
                .andExpect(jsonPath("$.order.disputeOpenedFromStatus").value("delivered"))
                .andExpect(jsonPath("$.reviewContext.disputeOpenedByAccountId").value("acct-buyer"))
                .andExpect(content().string(containsString("cancel_dispute")));

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/cancel-dispute")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "reason": "双方确认素材有效，撤回争议继续验收"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.payload.restoredStatus").value("DELIVERED"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select status from work_items where item_no = ? limit 1",
                String.class,
                reviewItemNo)).isEqualTo("closed");

        mockMvc.perform(get("/api/v1/orders/" + orderNo).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("delivered"))
                .andExpect(jsonPath("$.order.disputeCancelledByAccountId").value("acct-buyer"))
                .andExpect(jsonPath("$.reviewContext.disputeCancelReason").value("双方确认素材有效，撤回争议继续验收"))
                .andExpect(content().string(containsString("accept_order")));

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/accept")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acceptedByAccountId": "acct-buyer",
                                  "note": "撤回争议后验收交付"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_OPEN"));
    }

    @Test
    void disputedOrderFreezesSettlementAndReviewerClosesOriginal() throws Exception {
        String offerId = createOffer("AI 交付店");
        var workspace = mockMvc.perform(get("/api/v1/posts/" + offerNo(offerId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "buyerNote": "交付一份可下载素材"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);

        markOrderPaymentCaptured(orderId);

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "summary": "agent 交付素材链接",
                                  "output": {
                                    "url": "https://example.com/assets.zip"
                                  },
                                  "sourceReceipt": {
                                    "runId": "run-dispute-001"
                                  },
                                  "agentRuntime": "runtime-seller-mac"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        var disputeReceipt = mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/dispute")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "reason": "素材链接内容与验收条款不一致",
                                  "evidenceRefs": ["https://example.com/evidence/broken-link"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"))
                .andReturn();
        String reviewItemNo = JsonTestSupport.readString(disputeReceipt.getResponse().getContentAsString(), "/payload/reviewItemNo");

        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId)).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.settlementFrozen").value(true))
                .andExpect(jsonPath("$.order.reviewStatus").value("open"))
                .andExpect(jsonPath("$.order.disputeWindowStatus").value("closed"))
                .andExpect(jsonPath("$.order.acceptanceCriteriaSnapshot[0]").value("数字内容可打开"));

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("resolve_disputed_order")))
                .andExpect(content().string(containsString("数字内容可打开")));

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-member")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("resolve_disputed_order"))));

        mockMvc.perform(post("/api/v1/work/items/" + reviewItemNo + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-reviewer",
                                  "executionMode": "human"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("claimed"));

        mockMvc.perform(post("/api/v1/work/items/" + reviewItemNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-reviewer",
                                  "summary": "评审确认交付链接不可用，原订单关闭。",
                                  "output": {
                                    "decision": "close_original",
                                    "source": "work-review-test"
                                  },
                                  "evidenceRefs": ["https://example.com/evidence/broken-link"],
                                  "traceRefs": ["work-review:test"],
                                  "contentHashes": [],
                                  "links": [],
                                  "artifacts": [],
                                  "agentRuntime": "manual-review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"));

        mockMvc.perform(post("/api/v1/work/items/" + reviewItemNo + "/review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewerAccountId": "acct-reviewer",
                                  "decision": "disputed",
                                  "reason": "采纳评审结论，关闭原订单",
                                  "evidenceRefs": ["https://example.com/evidence/broken-link"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.decision").value("disputed"));

        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId)).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("final_closed"))
                .andExpect(jsonPath("$.order.settlementFrozen").value(false))
                .andExpect(jsonPath("$.order.reviewStatus").value("resolved"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from work_reviews wr join work_runs run on run.id = wr.work_run_id join work_items item on item.id = run.work_item_id where item.item_no = ?",
                Integer.class,
                reviewItemNo)).isEqualTo(1);
    }

    @Test
    void backofficeOverrideMarksReviewerMaliciousWhenDecisionIsOverturned() throws Exception {
        String offerId = createOffer("AI 评审风控店");
        var workspace = mockMvc.perform(get("/api/v1/posts/" + offerNo(offerId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "buyerNote": "交付一份可下载素材"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);

        markOrderPaymentCaptured(orderId);

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "summary": "agent 交付素材链接",
                                  "output": {
                                    "url": "https://example.com/assets.zip"
                                  },
                                  "sourceReceipt": {
                                    "runId": "run-override-001"
                                  },
                                  "agentRuntime": "runtime-seller-mac"
                                }
                                """))
                .andExpect(status().isOk());

        var disputeReceipt = mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/dispute")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "reason": "素材链接内容与验收条款不一致",
                                  "evidenceRefs": ["https://example.com/evidence/broken-link"]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String reviewItemNo = JsonTestSupport.readString(disputeReceipt.getResponse().getContentAsString(), "/payload/reviewItemNo");

        mockMvc.perform(get("/api/v1/workbench").with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("resolve_disputed_order")));

        mockMvc.perform(post("/api/v1/work/items/" + reviewItemNo + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-reviewer",
                                  "executionMode": "human"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("claimed"));

        mockMvc.perform(post("/api/v1/work/items/" + reviewItemNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-reviewer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-reviewer",
                                  "summary": "评审给出接受原订单的结论。",
                                  "output": {
                                    "decision": "accept_original",
                                    "source": "override-test"
                                  },
                                  "evidenceRefs": ["https://example.com/evidence/reviewer-note"],
                                  "traceRefs": ["work-review:override"],
                                  "contentHashes": [],
                                  "links": [],
                                  "artifacts": [],
                                  "agentRuntime": "manual-review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"));

        mockMvc.perform(post("/api/v1/work/orders/" + orderNo(orderId) + "/override-review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "decision": "CLOSE_ORIGINAL",
                                  "reason": "review proof ignored buyer evidence"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL_CLOSED"));

        mockMvc.perform(get("/api/v1/orders/" + orderNo(orderId)).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("final_closed"))
                .andExpect(jsonPath("$.order.reviewStatus").value("resolved"))
                .andExpect(jsonPath("$.order.backofficeOverrideDecision").value("close_original"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select status from work_items where item_no = ? limit 1",
                String.class,
                reviewItemNo)).isEqualTo("closed");
    }

    @Test
    void backofficeOverrideAcceptsOriginalAndClosesWorkReviewItem() throws Exception {
        DisputedOrderFixture fixture = createDeliveredDisputedOfferOrder("AI 高管确认店", "run-override-accept-001");

        mockMvc.perform(post("/api/v1/work/orders/" + fixture.orderNo() + "/override-review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "decision": "ACCEPT_ORIGINAL",
                                  "reason": "executive confirms original delivery"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL_ACCEPTED"));

        mockMvc.perform(get("/api/v1/orders/" + fixture.orderNo()).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("final_accepted"))
                .andExpect(jsonPath("$.order.settlementFrozen").value(false))
                .andExpect(jsonPath("$.order.reviewStatus").value("resolved"))
                .andExpect(jsonPath("$.order.backofficeOverrideDecision").value("accept_original"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select status from work_items where item_no = ? limit 1",
                String.class,
                fixture.reviewItemNo())).isEqualTo("accepted");
    }

    @Test
    void memberCannotOverrideDisputedOrder() throws Exception {
        DisputedOrderFixture fixture = createDeliveredDisputedOfferOrder("AI 越权争议店", "run-override-denied-001");

        mockMvc.perform(post("/api/v1/work/orders/" + fixture.orderNo() + "/override-review")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-member",
                                  "decision": "CLOSE_ORIGINAL",
                                  "reason": "member tries to resolve dispute"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/orders/" + fixture.orderNo()).with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("disputed"))
                .andExpect(jsonPath("$.order.reviewStatus").value("open"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "select status from work_items where item_no = ? limit 1",
                String.class,
                fixture.reviewItemNo())).isEqualTo("ready");
    }

    private String createOffer(String title) throws Exception {
        var offerCreate = mockMvc.perform(post("/api/v1/offers")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                   "description": "卖虚拟物品。",
                                   "currency": "USD",
                                   "paymentMethod": "okx_direct_pay",
                                   "paymentRecipient": "0x1111111111111111111111111111111111111111",
                                   "items": [
                                    {
                                      "name": "默认数字内容",
                                      "description": "默认发布 item。",
                                      "deliveryStandard": "按订单要求交付数字内容。",
                                      "acceptanceCriteria": ["数字内容可打开", "内容符合订单备注"],
                                      "amount": 100,
                                      "quantity": 5
                                    }
                                  ]
                                }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonTestSupport.readString(offerCreate.getResponse().getContentAsString(), "/offer/id");
    }

    private DisputedOrderFixture createDeliveredDisputedOfferOrder(String title, String runId) throws Exception {
        String offerId = createOffer(title);
        var workspace = mockMvc.perform(get("/api/v1/posts/" + offerNo(offerId) + "/workspace"))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = JsonTestSupport.readString(workspace.getResponse().getContentAsString(), "/items/0/id");

        var claimReceipt = mockMvc.perform(post("/api/v1/items/" + itemId + "/claim")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "buyerNote": "交付一份可下载素材"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String orderId = JsonTestSupport.readString(claimReceipt.getResponse().getContentAsString(), "/subjectId");
        String orderNo = orderNo(orderId);
        markOrderPaymentCaptured(orderId);

        mockMvc.perform(post("/api/v1/work/items/wb-delivery-result-" + orderNo + "/receipt")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-seller"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-seller",
                                  "summary": "agent 交付素材链接",
                                  "output": {
                                    "url": "https://example.com/assets.zip"
                                  },
                                  "sourceReceipt": {
                                    "runId": "%s"
                                  },
                                  "agentRuntime": "runtime-seller-mac"
                                }
                                """.formatted(runId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        var disputeReceipt = mockMvc.perform(post("/api/v1/work/orders/" + orderNo + "/dispute")
                        .with(SecurityTestSupport.session(jdbcTemplate, "acct-buyer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actorAccountId": "acct-buyer",
                                  "reason": "素材链接内容与验收条款不一致",
                                  "evidenceRefs": ["https://example.com/evidence/broken-link"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"))
                .andReturn();
        String reviewItemNo = JsonTestSupport.readString(disputeReceipt.getResponse().getContentAsString(), "/payload/reviewItemNo");
        return new DisputedOrderFixture(orderId, orderNo, reviewItemNo);
    }

    private String offerNo(String offerId) {
        return jdbcTemplate.queryForObject("select offer_no from offers where id = ?", String.class, offerId);
    }

    private String orderNo(String orderId) {
        return jdbcTemplate.queryForObject("select order_no from orders where id = ? or order_no = ?", String.class, orderId, orderId);
    }

    private String orderId(String orderIdOrNo) {
        return jdbcTemplate.queryForObject("select id from orders where id = ? or order_no = ?", String.class, orderIdOrNo, orderIdOrNo);
    }

    private void markOrderPaymentCaptured(String orderIdOrNo) {
        String orderId = orderId(orderIdOrNo);
        jdbcTemplate.update("""
                        insert into payment_intents (
                          id, payment_no, order_id, account_id, provider, provider_payment_ref, status, amount_minor,
                          currency, callback_token, captured_at, metadata, created_at, updated_at
                        ) values (?, ?, ?, 'acct-buyer', 'okx', ?, 'captured'::payment_intent_status, 10000,
                          'USD', ?, now(), '{"paymentMethod":"okx_direct_pay","okxReconciliation":{"verifyValid":true,"settleSuccess":true,"settleStatus":"success","chainReceiptStatus":"success","transferLogCount":1,"txHash":"0xtest"}}'::jsonb, now(), now())
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

    private record DisputedOrderFixture(String orderId, String orderNo, String reviewItemNo) {
    }
}
