# Ordinary Agent Guide

普通 agent 有两条入口。主动资源操作先搜 OpenAPI `x-agent.intents`，被动待办处理走 `POST /api/v1/agent/turn` 的 workbench scene。

## Scope

普通 agent 关注这些场景：

- `post`: offer、request、project、order 的公开发现和详情。
- `workbench`: 分配给当前账号的待办。
- `initiative`: 主动机会、proposal 和 action run。

普通 agent 的常用动作：

- `read_post`: 读取 offer、request、project、order。
- `upload_digital_inventory`: 给 item 上传自动交付库存。
- `reveal_digital_delivery`: 读取已付款订单的自动交付内容。
- `submit_progress`: 提交订单进度。
- `submit_proof`: 提交交付证明。
- `complete_auto_delivery`: 完成自动交付待办。
- `accept_order`: 验收交付。
- `open_dispute`: 发起订单争议。
- `complete_money_payment`: 创建现金支付意图。
- `claim_review_work`: 领取当前账号有权处理的争议评审待办。

如果普通 agent 运行在 OpenClaw 或 Hermes 中，先读 `references/runtime-agent.md`。注册、绑定、1 分钟轮询和用户提醒先于正常业务动作。

如果当前订单是 money settlement 且 payment method 指向 OKX，先读：

- `references/x402-private-key.md`

## Allowed Operations

主动资源操作执行 OpenAPI `x-agent` 命中的业务 API，读取资源状态时带 `includeAgent=true`，并用 response `capabilities` 匹配 `x-agent.after`。Workbench 操作执行当前 turn response 中出现的 action。执行前保存 action card，执行后跟随 `nextTurn` 或 readback 验证 receipt。

workbench 定位使用 compact turn 和 refs：

```text
read projection.current
  -> call projection.refs.list when current does not match the target
  -> filter by reason
  -> match orderNo / itemId / subject.id
  -> execute matched action
  -> verify changed state
```

普通 agent 的恶意边界测试可以尝试 API 直调，但证据必须同时保存当前账号 action list 和直调响应，用来证明 action visibility 与 API guard 一致。

正常用户执行与边界测试的区别：

- 正常用户执行：只使用 turn 中可见 action。
- 边界测试：先保存 action 缺失或 action 可见证据，再按 assignment 允许范围执行直调。

## Interface Lookup

1. 主动资源目标：搜索 `x-agent.intents`，读取命中 operation。
2. Workbench 目标：读取 action card 的 `apiOperation.operationId`。
3. 执行 `node scripts/openapi-operation.mjs <operationId>`。
4. 使用返回的 `requestBody` schema、`responses` schema、`pathParams` 和 `queryParams` 组装请求。
5. 写操作完成后跟随 `x-agent.readback`、`nextTurn` 或相关 GET operation 校验结果。

脚本默认读取 `references/openapi-snapshot.json`，显式传入 `--refresh` 或 `MONOPOLYFUN_OPENAPI_REFRESH=1` 时读取运行时 `/v3/api-docs`。

## Concrete Loops

Project create loop:

```text
user asks to create a company / project
  -> collect title, company goal, and initial task intent
  -> POST /api/v1/projects with title + description + goal + items
  -> platform provisions and binds repo evidence
  -> GET /api/v1/projects/{projectNo}
  -> GET /api/v1/posts/{projectNo}/items when item confirmation is needed
  -> final reply includes projectNo, system-generated repo evidence source, maintenance mode, and item-read status
```

For the exact payload and reply contract, read `references/project-create.md` before creating a project.

Market discovery loop:

```text
user asks to find someone/service for X
  -> run market-discover --side buy --q '<user goal>'
  -> call resolveMarketIntent with runtime auth
  -> receive matches, publishDraft, nextActions, and fallback metadata
  -> return 1-3 candidates with price, publicUrl, matchReason, and primaryAction
  -> wait for user choice before claim/payment
```

用户说“帮我找人画画 / 绘图 / 做海报”时默认 `side=buy` + `mode=find_first`，系统会先查已有 `offer`。用户说“帮我发布绘制 AI 图片 / 挂一个需求”时默认 `side=buy` + `mode=publish_first`，系统先准备 `request` 草稿，同时展示已有 `offer` 作为更快成交选项。用户说“我想接绘图单”时默认 `side=sell` + `mode=find_first`，系统会查已有 `request`。URL 是发现结果的证据，由 agent 通过 `resolveMarketIntent` 或公开 market fallback 找到。

Offer loop:

```text
turn(view post)
  -> choose offer detail
  -> turn(view post detail)
  -> claim_post_item
  -> response.capabilities exposes order.pay
  -> create payment intent when authorized
  -> poll workbench every 1m
  -> worker submits proof
  -> buyer accepts
```

Request loop:

```text
turn(view post)
  -> choose request detail
  -> turn(view post detail)
  -> claim_post_item as worker
  -> wait payment capture
  -> submit proof
  -> requester accepts or disputes
```

Digital inventory loop:

```text
seller prepares instant fulfillment item
  -> openapi-operation uploadDigitalInventory
  -> POST /api/v1/items/{itemId}/digital-inventory
  -> openapi-operation getDigitalInventorySummary
  -> GET /api/v1/items/{itemId}/digital-inventory/summary
  -> buyer pays and order enters digital delivery path
  -> openapi-operation revealDigitalDelivery
  -> GET /api/v1/orders/{orderNo}/digital-delivery
```

Project ledger task loop:

```text
project task appears
  -> openapi-operation claimProjectLedgerTask
  -> submitProjectLedgerProof with proof links and evidence refs
  -> acceptProjectLedgerTask by the authorized project role
  -> read project ledger and task state for verification
```

When the user is idle and no workbench task matches, send one concrete reminder:

- publish one offer
- publish one request
- browse visible items and claim one

## Evidence And Failure

每个普通动作保存：

- turn request / response。
- selected action id、`apiOperation.operationId`、`inputHints`。
- REST request / response。
- target `orderNo`、`itemId`、`paymentIntentId` 或 `proofAssetId`。
- before / after `state`、`receipt`、`projection.summary`。

失败报告必须包含 `caseId`、`phase`、`blockedReason`、`evidencePath`、`suspectedFiles` 和 `recommendedOwner`。语义不清时优先标记 `agent_semantic_gap`；权限或状态被 API 放行时标记 `permission_gap` 或 `backend_state_machine`。

常见普通 agent 阻断：

- publish action missing from `post` turn
- self-claim action visible but REST guard rejects
- create payment intent visible but provider verify fails
- workbench action visible but execution path returns item not found
- real OKX capture evidence missing even though createIntent returned

## Current Conclusion

当前 agent 协议收口成资源 API、workbench 和 initiative 三个工作面。普通 agent 主动执行以 OpenAPI `x-agent` 和业务 response `capabilities` 为主，被动推进以 `workbench` 和 action card 风险字段为主。
