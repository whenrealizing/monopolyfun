package com.monopolyfun.platform.agent.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentOpenApiCustomizer implements OpenApiCustomizer {
    @Override
    public void customise(OpenAPI openApi) {
        if (openApi == null || openApi.getPaths() == null) {
            return;
        }
        Paths paths = openApi.getPaths();
        add(paths, "/api/v1/offers", PathItem.HttpMethod.POST, publishOffer());
        add(paths, "/api/v1/offers/{offerNo}", PathItem.HttpMethod.GET, getOffer());
        add(paths, "/api/v1/requests", PathItem.HttpMethod.POST, publishRequest());
        add(paths, "/api/v1/requests/{requestNo}", PathItem.HttpMethod.GET, getRequest());
        add(paths, "/api/v1/projects", PathItem.HttpMethod.POST, publishProject());
        add(paths, "/api/v1/projects/{projectNo}", PathItem.HttpMethod.GET, getProject());
        add(paths, "/api/v1/projects/{projectNo}/commercialization", PathItem.HttpMethod.GET, getProjectCommercialization());
        add(paths, "/api/v1/projects/{projectNo}/launches", PathItem.HttpMethod.POST, createValidationLaunch());
        add(paths, "/api/v1/projects/{projectNo}/launches/{launchId}/tasks", PathItem.HttpMethod.POST, createValidationTask());
        add(paths, "/api/v1/projects/{projectNo}/tasks/{taskId}/proof", PathItem.HttpMethod.POST, submitValidationProof());
        add(paths, "/api/v1/projects/{projectNo}/validation-feedback", PathItem.HttpMethod.POST, submitValidationFeedback());
        add(paths, "/api/v1/initiative/projects/{projectNo}/recommendations/generate", PathItem.HttpMethod.POST, generateProjectRecommendations());
        add(paths, "/api/v1/projects/{projectNo}/roles/{roleCode}/assign", PathItem.HttpMethod.POST, assignProjectRole());
        add(paths, "/api/v1/projects/{projectNo}/roles/{roleCode}/invite", PathItem.HttpMethod.POST, inviteProjectRole());
        add(paths, "/api/v1/posts/{postNo}/items", PathItem.HttpMethod.GET, listPostItems());
        add(paths, "/api/v1/posts/{postNo}/items", PathItem.HttpMethod.POST, createPostItem());
        add(paths, "/api/v1/projects/{projectNo}/items", PathItem.HttpMethod.POST, createProjectItem());
        add(paths, "/api/v1/items/{itemId}/claim", PathItem.HttpMethod.POST, claimPostItem());
        add(paths, "/api/v1/orders/{orderNo}", PathItem.HttpMethod.GET, getOrder());
        add(paths, "/api/v1/payments/orders/{orderNo}/intent", PathItem.HttpMethod.POST, createPaymentIntent());
        add(paths, "/api/v1/payments/intents/{intentId}", PathItem.HttpMethod.GET, getPaymentIntent());
        add(paths, "/api/v1/payments/intents/{intentId}/refresh", PathItem.HttpMethod.POST, refreshPaymentIntent());
        add(paths, "/api/v1/payments/intents/{intentId}/refund", PathItem.HttpMethod.POST, refundPaymentIntent());
        add(paths, "/api/v1/payments/intents/{intentId}/cancel", PathItem.HttpMethod.POST, cancelPaymentIntent());
        add(paths, "/api/v1/payments/intents/{intentId}/dispute", PathItem.HttpMethod.POST, disputePaymentIntent());
        add(paths, "/api/v1/share-release-requests/pending/me", PathItem.HttpMethod.GET, listShareReleaseRequests());
        add(paths, "/api/v1/share-release-requests/{requestId}/approve", PathItem.HttpMethod.POST, approveShareReleaseRequest());
    }

    private void add(Paths paths, String path, PathItem.HttpMethod method, Map<String, Object> extension) {
        PathItem item = paths.get(path);
        if (item == null) {
            return;
        }
        Operation operation = item.readOperationsMap().get(method);
        if (operation == null) {
            return;
        }
        // 中文注释：x-agent 跟随 OpenAPI 一起生成，agent 离线或在线读取同一份接口合同。
        operation.addExtension("x-agent", extension);
    }

    private Map<String, Object> publishOffer() {
        return extension(
                List.of("publish offer", "发布报价", "创建服务商品", "出售代码维护服务"),
                "创建一个 offer，并生成初始任务。agent 需要资源能力字段时传 includeAgent=true。",
                resource("post.offer", "$.offer.offerNo"),
                readback("getOffer", Map.of("offerNo", "$.offer.offerNo", "includeAgent", true)),
                List.of(
                        after("offer.create_item", "新增任务项", "createPostItem", "POST", "/api/v1/posts/{postNo}/items",
                                "给当前 offer 新增一个可领取 item。", "low", false, Map.of("postNo", "$.offer.offerNo")),
                        after("offer.close", "关闭报价", "closeOffer", "POST", "/api/v1/offers/{offerNo}/close",
                                "关闭当前 offer，关闭后停止接单。", "destructive", true, Map.of("offerNo", "$.offer.offerNo"))));
    }

    private Map<String, Object> getOffer() {
        return extension(
                List.of("view offer", "查看报价", "读取服务商品"),
                "读取 offer 当前业务状态；includeAgent=true 时返回 resourceKey、capabilities 和 blockedCapabilities。",
                resource("post.offer", "$.offerNo"),
                null,
                List.of(
                        after("offer.create_item", "新增任务项", "createPostItem", "POST", "/api/v1/posts/{postNo}/items",
                                "给当前 offer 新增一个可领取 item。", "low", false, Map.of("postNo", "$.offerNo")),
                        after("offer.close", "关闭报价", "closeOffer", "POST", "/api/v1/offers/{offerNo}/close",
                                "关闭当前 offer，关闭后停止接单。", "destructive", true, Map.of("offerNo", "$.offerNo"))));
    }

    private Map<String, Object> publishRequest() {
        return extension(
                List.of("publish request", "发布需求", "创建悬赏需求", "找人完成任务"),
                "创建一个 request，并生成初始任务。agent 需要资源能力字段时传 includeAgent=true。",
                resource("post.request", "$.request.requestNo"),
                readback("getRequest", Map.of("requestNo", "$.request.requestNo", "includeAgent", true)),
                List.of(
                        after("request.create_item", "新增需求项", "createPostItem", "POST", "/api/v1/posts/{postNo}/items",
                                "给当前 request 新增一个可承接 item。", "low", false, Map.of("postNo", "$.request.requestNo")),
                        after("request.close", "关闭需求", "closeRequest", "POST", "/api/v1/requests/{requestNo}/close",
                                "关闭当前 request。", "destructive", true, Map.of("requestNo", "$.request.requestNo"))));
    }

    private Map<String, Object> getRequest() {
        return extension(
                List.of("view request", "查看需求", "读取悬赏需求"),
                "读取 request 当前业务状态；includeAgent=true 时返回 resourceKey、capabilities 和 blockedCapabilities。",
                resource("post.request", "$.requestNo"),
                null,
                List.of(
                        after("request.create_item", "新增需求项", "createPostItem", "POST", "/api/v1/posts/{postNo}/items",
                                "给当前 request 新增一个可承接 item。", "low", false, Map.of("postNo", "$.requestNo")),
                        after("request.close", "关闭需求", "closeRequest", "POST", "/api/v1/requests/{requestNo}/close",
                                "关闭当前 request。", "destructive", true, Map.of("requestNo", "$.requestNo"))));
    }

    private Map<String, Object> publishProject() {
        return extension(
                List.of("publish project", "创建项目", "发布项目", "创建 agent 项目"),
                "创建 project，并生成初始 project item。agent 需要资源能力字段时传 includeAgent=true。",
                resource("project", "$.project.projectNo"),
                readback("getProject", Map.of("projectNo", "$.project.projectNo", "includeAgent", true)),
                List.of(
                        after("project.create_item", "新增项目任务", "createProjectItem", "POST", "/api/v1/projects/{projectNo}/items",
                                "给当前 project 新增任务。", "low", false, Map.of("projectNo", "$.project.projectNo")),
                        after("project.recommend_role_candidate", "推荐角色候选人", "generateProjectRecommendations", "POST", "/api/v1/initiative/projects/{projectNo}/recommendations/generate",
                                "生成维护席位缺口推荐。", "low", false, Map.of("projectNo", "$.project.projectNo")),
                        after("project.send_role_invite", "发送角色邀请", "inviteProjectRole", "POST", "/api/v1/projects/{projectNo}/roles/{roleCode}/invite",
                                "把维护席位邀请发送到对方 workbench，接受后生成授权任务。", "role_assignment", true, Map.of("projectNo", "$.project.projectNo")),
                        after("project.appoint_role", "直接任命角色", "assignProjectRole", "POST", "/api/v1/projects/{projectNo}/roles/{roleCode}/assign",
                                "给当前 project 指派角色并生成角色任务。", "role_assignment", true, Map.of("projectNo", "$.project.projectNo")),
                        after("project.commercialization.view", "查看商业化账本", "getProjectCommercialization", "GET", "/api/v1/projects/{projectNo}/commercialization",
                                "读取 direction、proof、revenue 和 shares 分配状态。", "low", false, Map.of("projectNo", "$.project.projectNo")),
                        after("project.validation_feedback.submit", "提交核验反馈", "createFeedback", "POST", "/api/v1/projects/{projectNo}/validation-feedback",
                                "提交产品、proof、部署 URL 或 release 的核验反馈。", "low", false, Map.of("projectNo", "$.project.projectNo"))));
    }

    private Map<String, Object> getProject() {
        return extension(
                List.of("view project", "查看项目", "读取项目"),
                "读取 project 当前业务状态；includeAgent=true 时返回项目能力字段。",
                resource("project", "$.projectNo"),
                null,
                List.of(
                        after("project.create_item", "新增项目任务", "createProjectItem", "POST", "/api/v1/projects/{projectNo}/items",
                                "给当前 project 新增任务。", "low", false, Map.of("projectNo", "$.projectNo")),
                        after("project.recommend_role_candidate", "推荐角色候选人", "generateProjectRecommendations", "POST", "/api/v1/initiative/projects/{projectNo}/recommendations/generate",
                                "生成维护席位缺口推荐。", "low", false, Map.of("projectNo", "$.projectNo")),
                        after("project.send_role_invite", "发送角色邀请", "inviteProjectRole", "POST", "/api/v1/projects/{projectNo}/roles/{roleCode}/invite",
                                "把维护席位邀请发送到对方 workbench，接受后生成授权任务。", "role_assignment", true, Map.of("projectNo", "$.projectNo")),
                        after("project.appoint_role", "直接任命角色", "assignProjectRole", "POST", "/api/v1/projects/{projectNo}/roles/{roleCode}/assign",
                                "给当前 project 指派角色并生成角色任务。", "role_assignment", true, Map.of("projectNo", "$.projectNo")),
                        after("project.commercialization.view", "查看商业化账本", "getProjectCommercialization", "GET", "/api/v1/projects/{projectNo}/commercialization",
                                "读取 direction、proof、revenue 和 shares 分配状态。", "low", false, Map.of("projectNo", "$.projectNo")),
                        after("project.validation_feedback.submit", "提交核验反馈", "createFeedback", "POST", "/api/v1/projects/{projectNo}/validation-feedback",
                                "提交产品、proof、部署 URL 或 release 的核验反馈。", "low", false, Map.of("projectNo", "$.projectNo"))));
    }

    private Map<String, Object> getProjectCommercialization() {
        return extension(
                List.of("view project commercialization", "查看商业化账本", "查看项目收入和股份分配"),
                "读取 project 的方向验证、proof 统计、收入池和当前股份分配状态。",
                resource("project.commercialization", "$.projectNo"),
                null,
                List.of(after("project.validation_launch.create", "创建 Launch Card", "createLaunch", "POST", "/api/v1/projects/{projectNo}/launches",
                                "创建新的商业化验证轮次。", "low", false, Map.of("projectNo", "$.projectNo")),
                        after("project.validation_feedback.submit", "提交核验反馈", "createFeedback", "POST", "/api/v1/projects/{projectNo}/validation-feedback",
                                "提交产品、proof、部署 URL 或 release 的核验反馈。", "low", false, Map.of("projectNo", "$.projectNo"))));
    }

    private Map<String, Object> createValidationLaunch() {
        return extension(
                List.of("create launch card", "创建 Launch Card", "发起项目验证"),
                "创建 Project Validation Launch，proofRequest 使用开放 schema。",
                resource("project.validation_launch", "$.id"),
                readback("getProjectCommercialization", Map.of("projectNo", "$.projectNo")),
                List.of(after("project.validation_task.create", "创建 Task", "createTask", "POST", "/api/v1/projects/{projectNo}/launches/{launchId}/tasks",
                        "在 Launch Card 下创建可领取任务。", "low", false, Map.of("projectNo", "$.projectNo", "launchId", "$.id"))));
    }

    private Map<String, Object> createValidationTask() {
        return extension(
                List.of("create validation task", "创建 Task", "生成验证任务"),
                "在 Launch Card 下创建 Task，字段保持开放 schema。",
                resource("project.validation_task", "$.id"),
                readback("getProjectCommercialization", Map.of("projectNo", "$.projectNo")),
                List.of(after("project.validation_proof.submit", "提交 Proof", "submitProof", "POST", "/api/v1/projects/{projectNo}/tasks/{taskId}/proof",
                        "为当前 Task 提交公开证据。", "low", false, Map.of("projectNo", "$.projectNo", "taskId", "$.id"))));
    }

    private Map<String, Object> submitValidationProof() {
        return extension(
                List.of("submit validation proof", "提交项目 proof", "提交部署或 PR 证据"),
                "提交 Project Validation Proof；evidenceItems 使用开放 schema。",
                resource("project.validation_proof", "$.id"),
                readback("getProjectCommercialization", Map.of("projectNo", "$.projectNo")),
                List.of(after("project.commercialization.view", "查看商业化账本", "getProjectCommercialization", "GET", "/api/v1/projects/{projectNo}/commercialization",
                        "proof 提交后读取商业化账本。", "low", false, Map.of("projectNo", "$.projectNo"))));
    }

    private Map<String, Object> submitValidationFeedback() {
        return extension(
                List.of("submit project feedback", "提交核验反馈", "反馈部署链接失效", "反馈 proof 问题"),
                "提交用户核验反馈；subjectType 指向 proof、部署 URL、release、任务或治理决定，feedbackType 描述问题类型。",
                resource("project.validation_feedback", "$.id"),
                readback("getProjectCommercialization", Map.of("projectNo", "$.projectNo")),
                List.of(after("project.commercialization.view", "查看商业化账本", "getProjectCommercialization", "GET", "/api/v1/projects/{projectNo}/commercialization",
                        "反馈提交后读取商业化账本。", "low", false, Map.of("projectNo", "$.projectNo"))));
    }

    private Map<String, Object> generateProjectRecommendations() {
        return extension(
                List.of("recommend role candidate", "推荐维护席位候选人", "生成维护席位分工建议"),
                "生成维护席位缺口推荐；授权推荐进入 workbench，低风险动作可起草邀请，高风险动作需要用户审批。",
                resource("project.recommendation[]", "$[*].recommendationNo"),
                null,
                List.of(
                        after("project.send_role_invite", "发送角色邀请", "inviteProjectRole", "POST", "/api/v1/projects/{projectNo}/roles/{roleCode}/invite",
                                "发送维护席位邀请到目标账号 workbench。", "role_assignment", true, Map.of("projectNo", "$.projectNo", "roleCode", "$.targetRoleCode")),
                        after("project.appoint_role", "直接任命角色", "assignProjectRole", "POST", "/api/v1/projects/{projectNo}/roles/{roleCode}/assign",
                                "把目标账号直接任命到维护席位。", "role_assignment", true, Map.of("projectNo", "$.projectNo", "roleCode", "$.targetRoleCode"))));
    }

    private Map<String, Object> assignProjectRole() {
        return extension(
                List.of("appoint project role", "直接任命角色", "分配项目职务"),
                "把目标账号直接任命到维护席位，并生成该授权的初始任务。",
                resource("project.role", "$.roleCode"),
                null,
                List.of());
    }

    private Map<String, Object> inviteProjectRole() {
        return extension(
                List.of("invite project role", "邀请维护席位", "邀请成员加入项目", "邀请维护席位成员"),
                "发送维护席位邀请到目标账号 workbench；对方接受后写入 project_roles 并生成授权任务。",
                resource("project.role_invite", "$.workItemNo"),
                null,
                List.of(after("workbench.accept_project_invite", "接受项目邀请", "acceptProjectInvite", "POST", "/api/v1/workbench/{itemId}/project-invite/accept",
                        "被邀请账号在 workbench 中接受维护席位邀请。", "low", false, Map.of("itemId", "$.workItemNo"))));
    }

    private Map<String, Object> listPostItems() {
        return extension(
                List.of("list post items", "查看任务项", "读取报价任务项", "读取需求任务项"),
                "读取 offer 或 request 下的 item 列表；includeAgent=true 时返回 item 级能力字段。",
                resource("post.item[]", "$[*].id"),
                null,
                List.of(after("post_item.claim", "领取任务项", "claimPostItem", "POST", "/api/v1/items/{itemId}/claim",
                        "领取当前可执行 item 并生成订单。", "low", false, Map.of("itemId", "$.id"))));
    }

    private Map<String, Object> createPostItem() {
        return extension(
                List.of("create post item", "新增任务项", "添加报价 item", "添加需求 item"),
                "给 offer 或 request 新增一个可领取 item。agent 后续读取 item 时传 includeAgent=true。",
                resource("post.item", "$.id"),
                readback("getPostItem", Map.of("itemId", "$.id", "includeAgent", true)),
                List.of(after("post_item.close", "关闭任务项", "closePostItem", "POST", "/api/v1/items/{itemId}/close",
                        "关闭当前 item。", "destructive", true, Map.of("itemId", "$.id"))));
    }

    private Map<String, Object> createProjectItem() {
        return extension(
                List.of("create project item", "新增项目任务", "添加项目任务"),
                "给 project 新增一个可领取 item。agent 后续读取 item 时传 includeAgent=true。",
                resource("post.item", "$.id"),
                readback("getPostItem", Map.of("itemId", "$.id", "includeAgent", true)),
                List.of(after("post_item.claim", "领取项目任务", "claimPostItem", "POST", "/api/v1/items/{itemId}/claim",
                        "领取当前 project item 并生成订单。", "low", false, Map.of("itemId", "$.id"))));
    }

    private Map<String, Object> claimPostItem() {
        return extension(
                List.of("claim post item", "领取任务", "购买报价 item", "承接需求 item"),
                "领取一个可执行 item，成功后生成 order。",
                resource("order", "$.payload.orderNo"),
                readback("getOrder", Map.of("orderNo", "$.payload.orderNo", "includeAgent", true)),
                List.of(
                        after("order.pay", "支付订单", "createPaymentIntent", "POST", "/api/v1/payments/orders/{orderNo}/intent",
                                "为资金订单创建或刷新支付意图。", "payment_signing", true, Map.of("orderNo", "$.payload.orderNo")),
                        after("order.submit_proof", "提交交付", "submitWorkReceipt", "POST", "/api/v1/work/items/{itemId}/receipt",
                                "提交订单交付结果。", "low", false, Map.of("orderNo", "$.payload.orderNo"))));
    }

    private Map<String, Object> getOrder() {
        return extension(
                List.of("view order", "查看订单", "读取订单状态"),
                "读取订单状态和参与方；includeAgent=true 时返回当前账号可执行能力。",
                resource("order", "$.order.orderNo"),
                null,
                List.of(
                        after("order.pay", "支付订单", "createPaymentIntent", "POST", "/api/v1/payments/orders/{orderNo}/intent",
                                "为资金订单创建或刷新支付意图。", "payment_signing", true, Map.of("orderNo", "$.order.orderNo")),
                        after("order.submit_proof", "提交交付", "submitWorkReceipt", "POST", "/api/v1/work/items/{itemId}/receipt",
                                "提交订单交付结果。", "low", false, Map.of("orderNo", "$.order.orderNo")),
                        after("order.accept", "验收订单", "reviewWorkReceipt", "POST", "/api/v1/work/items/{itemId}/review",
                                "验收交付结果并打开争议窗口。", "dispute", true, Map.of("orderNo", "$.order.orderNo"))));
    }

    private Map<String, Object> createPaymentIntent() {
        return extension(
                List.of("create payment intent", "支付订单", "创建支付会话"),
                "为订单创建或刷新支付意图；includeAgent=true 时返回支付资源能力字段。",
                resource("payment.intent", "$.paymentIntent.id"),
                readback("getIntent", Map.of("intentId", "$.paymentIntent.id", "includeAgent", true)),
                List.of(
                        after("payment.refresh", "刷新支付状态", "refreshIntent", "POST", "/api/v1/payments/intents/{intentId}/refresh",
                                "从 provider 刷新支付状态。", "payment_signing", true, Map.of("intentId", "$.paymentIntent.id")),
                        after("payment.cancel", "取消支付", "cancelIntent", "POST", "/api/v1/payments/intents/{intentId}/cancel",
                                "取消当前支付意图。", "payment_signing", true, Map.of("intentId", "$.paymentIntent.id"))));
    }

    private Map<String, Object> getPaymentIntent() {
        return extension(
                List.of("view payment intent", "查看支付状态", "读取支付意图"),
                "读取支付意图当前状态；includeAgent=true 时返回 payment capabilities。",
                resource("payment.intent", "$.id"),
                null,
                List.of(
                        after("payment.refresh", "刷新支付状态", "refreshIntent", "POST", "/api/v1/payments/intents/{intentId}/refresh",
                                "从 provider 刷新支付状态。", "payment_signing", true, Map.of("intentId", "$.id")),
                        after("payment.refund", "退款", "refundIntent", "POST", "/api/v1/payments/intents/{intentId}/refund",
                                "对已捕获支付执行退款标记。", "payment_signing", true, Map.of("intentId", "$.id")),
                        after("payment.dispute", "标记支付争议", "disputeIntent", "POST", "/api/v1/payments/intents/{intentId}/dispute",
                                "把支付意图标记为争议状态。", "dispute", true, Map.of("intentId", "$.id"))));
    }

    private Map<String, Object> refreshPaymentIntent() {
        return paymentIntentWrite("refresh payment intent", "刷新支付状态", "刷新 provider 支付状态。");
    }

    private Map<String, Object> refundPaymentIntent() {
        return paymentIntentWrite("refund payment intent", "支付退款", "对支付意图执行退款状态转换。");
    }

    private Map<String, Object> cancelPaymentIntent() {
        return paymentIntentWrite("cancel payment intent", "取消支付", "取消支付意图。");
    }

    private Map<String, Object> disputePaymentIntent() {
        return paymentIntentWrite("dispute payment intent", "支付争议", "把支付意图标记为争议状态。");
    }

    private Map<String, Object> listShareReleaseRequests() {
        return extension(
                List.of("list share release approvals", "查看 shares 审批", "待审批 shares"),
                "读取当前账号可审批的 share release request；includeAgent=true 时返回审批能力。",
                resource("share.release_request[]", "$[*].id"),
                null,
                List.of(after("share_release.approve", "审批 shares 释放", "approveShareReleaseRequest", "POST", "/api/v1/share-release-requests/{requestId}/approve",
                        "批准当前 share release request。", "share_release", true, Map.of("requestId", "$.id"))));
    }

    private Map<String, Object> approveShareReleaseRequest() {
        return extension(
                List.of("approve share release", "审批 shares 释放"),
                "审批一个 share release request。",
                resource("share.release_request", "$.id"),
                null,
                List.of());
    }

    private Map<String, Object> paymentIntentWrite(String intent, String zhIntent, String description) {
        return extension(
                List.of(intent, zhIntent),
                description,
                resource("payment.intent", "$.id"),
                readback("getIntent", Map.of("intentId", "$.id", "includeAgent", true)),
                List.of(after("payment.refresh", "刷新支付状态", "refreshIntent", "POST", "/api/v1/payments/intents/{intentId}/refresh",
                        "从 provider 刷新支付状态。", "payment_signing", true, Map.of("intentId", "$.id"))));
    }

    private Map<String, Object> extension(
            List<String> intents,
            String description,
            Map<String, Object> resource,
            Map<String, Object> readback,
            List<Map<String, Object>> after) {
        LinkedHashMap<String, Object> extension = new LinkedHashMap<>();
        extension.put("intents", intents);
        extension.put("description", description);
        extension.put("resource", resource);
        if (readback != null) {
            extension.put("readback", readback);
        }
        extension.put("after", after);
        return extension;
    }

    private Map<String, Object> resource(String type, String idFrom) {
        return Map.of("type", type, "idFrom", idFrom);
    }

    private Map<String, Object> readback(String operationId, Map<String, Object> bindings) {
        return Map.of("operationId", operationId, "bindings", bindings);
    }

    private Map<String, Object> after(
            String capability,
            String intent,
            String operationId,
            String method,
            String path,
            String description,
            String risk,
            boolean requiresApproval,
            Map<String, Object> bindings) {
        LinkedHashMap<String, Object> action = new LinkedHashMap<>();
        action.put("capability", capability);
        action.put("intent", intent);
        action.put("operationId", operationId);
        action.put("method", method);
        action.put("path", path);
        action.put("description", description);
        action.put("risk", risk);
        action.put("requiresApproval", requiresApproval);
        action.put("bindings", bindings);
        return action;
    }
}
