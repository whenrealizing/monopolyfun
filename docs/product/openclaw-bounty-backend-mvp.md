# OpenClaw Bounty Backend MVP 改造方案

更新日期：2026-05-25

本文只定义后端第一阶段改造。产品体验主线见 `docs/product/company-agent-harness-experience.md`。

## 目标

后端第一阶段只做 `Work Thread facade`，复用现有执行、回执、验收和 shares 账本。

```text
WorkItem
  -> Work Thread packet
  -> Result.md
  -> Review
  -> Contribution settlement
  -> Revenue claim
```

验收结果：

```text
可以从 WorkItem 拉出 work-thread.md
可以提交 result.md 原文
可以校验 PR / Test / Changed Files
可以用 accept / resubmit / reject 推进状态
accepted 后可以生成 bounty settlement 和 shares ledger
Project Room 可以读到 My Rewards 的 shares / claimable USDC
```

## 当前可复用能力

```text
WorkController
  -> 已有 claim / receipt / review 写入口

WorkCommandService
  -> 已有 claimed / submitted / accepted / revision_requested 状态推进

work_receipts.output
  -> 可保存 resultMarkdown / prUrl / testSummary / changedFiles

shares_ledger
  -> 可保存 accepted 后的 contribution shares

ProjectCommercializationService
  -> 已有 revenue pool / distribution epoch 只读视图雏形
```

## 改造切片

### 1. WorkThreadController

新增对外入口：

```text
GET /api/v1/work-threads/{taskId}/packet
POST /api/v1/work-threads/{taskId}/result
POST /api/v1/work-threads/{taskId}/review
```

实现原则：

```text
对外命名使用 Work Thread
内部继续使用 WorkItem / WorkRun / WorkReceipt / WorkReview
权限复用 WorkQueryService 和 WorkCommandService
```

### 2. WorkThreadPacketService

从 `WorkItem` 生成 `work-thread.md`。

输入来源：

```text
work_items.title
work_items.goal
work_items.acceptance_criteria
work_items.input_refs
work_items.output_schema.taskValue
work_items.output_schema.bounty
work_items.output_schema.repoRef
work_items.output_schema.issueUrl
```

输出：

```text
projectNo
workThreadId
runtime
taskValue
bounty
goal
deliverables
acceptanceCriteria
repoRef
issueUrl
```

### 3. Result 提交

`POST /api/v1/work-threads/{taskId}/result` 接收：

```text
actorAccountId
resultMarkdown
summary
evidence[]
changedFiles[]
runtime
```

PR bounty 最小校验：

```text
resultMarkdown 存在
summary 有内容
PR 链接存在
Test 说明存在
changedFiles 有内容
frontmatter 匹配 projectNo / workThreadId
Work Thread 状态允许提交
```

落库：

```text
work_receipts.summary = summary
work_receipts.output.resultMarkdown = resultMarkdown
work_receipts.output.prUrl = parsed PR
work_receipts.output.testSummary = parsed Test
work_receipts.output.changedFiles = changedFiles
work_receipts.output.runtime = openclaw
work_receipts.evidence_refs = evidence[]
```

### 4. Review 简化

对外 decision：

```text
accept
resubmit
reject
```

内部映射：

```text
accept
  -> accepted

resubmit
  -> revision_requested

reject
  -> rejected
```

后端变更：

```text
WorkCommandService.normalizeDecision 支持 reject
work_items status check 增加 rejected
work_runs status check 增加 rejected
work_reviews status check 增加 rejected
WorkQueryService action 文案把 revise_receipt 显示为重新提交
```

### 5. ContributionSettlementService

`accepted` 后生成三类事实：

```text
contribution record
bounty settlement
contribution shares ledger
```

shares 计算第一阶段使用周期固定贡献池：

```text
periodMintBudget = 10,000 shares
periodAcceptedTaskValueTotal = 本周期 accepted 任务 Task Value 总和
taskShares = periodMintBudget * taskValue / periodAcceptedTaskValueTotal
```

第一阶段可先写入 pending shares：

```text
source_type = work_thread
source_id = work_run_id
account_id = actorAccountId
amount = pendingShares
reason = project_task
settlement_type_snapshot = shares
```

### 6. RevenueDistributionService

管理项目收款地址、周期快照和 claim proof。

需要新增三类对象：

```text
ProjectRevenueAddress
DistributionBatch
DistributionClaim
```

最小职责：

```text
保存 projectRevenueAddress
按 period 冻结 revenue / shares / wallet snapshot
生成 claimable amount
生成 Merkle proof
记录 claim txHash 和状态
```

## 数据库改造

### 修改现有表

```text
work_items
  -> output_schema 保存 taskValue / bounty / repoRef / issueUrl
  -> status check 增加 rejected

work_runs
  -> status check 增加 rejected

work_reviews
  -> status check 增加 rejected

work_receipts
  -> output 保存 resultMarkdown / prUrl / testSummary / changedFiles / runtime

shares_ledger
  -> 复用 source_type / source_id / account_id / amount
  -> source_type 使用 work_thread
  -> source_id 使用 work run id
```

### 新增 project_revenue_addresses

```text
id
project_id
chain_id
contract_address
token_address
status
created_at
updated_at
```

### 新增 distribution_batches

```text
id
project_id
period
total_revenue_minor
total_snapshot_shares
merkle_root
status
created_at
updated_at
```

### 新增 distribution_claims

```text
id
batch_id
account_id
wallet_address
amount_minor
proof
tx_hash
status
created_at
updated_at
```

## API 契约

```text
GET /api/v1/work-threads/{taskId}/packet
  -> 返回 WorkThreadPacket
  -> CLI 写 .monopolyfun/work-thread.md

POST /api/v1/work-threads/{taskId}/result
  -> 接收 resultMarkdown / summary / evidence / changedFiles
  -> 写入 work receipt
  -> 状态进入 submitted

POST /api/v1/work-threads/{taskId}/review
  -> accept / resubmit / reject
  -> accepted 后写 contribution record 和 settlement

POST /api/v1/projects/{id}/distributions/{period}/claim
  -> 返回 claimable amount 和 proof
  -> 用户钱包调用 RevenueDistributor.claim
```

## 实施顺序

1. 增加 `rejected` 状态迁移。
2. 新增 `WorkThreadController` 和 packet 读接口。
3. 新增 result 写接口，保存 `resultMarkdown` 到 `work_receipts.output`。
4. 新增 review facade，映射 `accept / resubmit / reject`。
5. 增加 accepted 后 contribution settlement 写入。
6. 增加 revenue address / distribution / claim 三张表和只读 claim 计算。
7. 接入链上 `RevenueDistributor.claim` proof。

## 第一阶段暂缓

```text
自动 PR / CI 读取
自动截图
复杂 evidence 目录
多 harness adapter
外部资金买入 bonding curve
二级市场
复杂治理投票
```
