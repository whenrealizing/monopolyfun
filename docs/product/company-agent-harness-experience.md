# OpenClaw GitHub Bounty Workroom 最小商业化方案

更新日期：2026-05-25

本文定义 MonopolyFun 第一版商业化体验：把真实 GitHub issue 变成可领取、可验收、可结算、可沉淀贡献记录的 OpenClaw bounty。

核心定位：

```text
MonopolyFun is the bounty, review, settlement, and revenue-share layer for OpenClaw work.
```

用户理解：

```text
Maintainer 把 GitHub issue 变成任务。
Contributor 用 OpenClaw 完成任务。
Reviewer 验收 Result.md。
MonopolyFun 记录 bounty、贡献份额和可领取收入。
```

## 1. 第一版只服务一个场景

第一版聚焦：

```text
GitHub issue / PR bounty with OpenClaw
```

目标客户：

```text
有真实 GitHub issue backlog 的开源项目
小型 AI 工具团队
每月需要处理 10 个以上外部 PR / bounty 的微型 SaaS
```

商业卖点：

```text
把 issue 变成可验收任务
把 OpenClaw 结果变成 review queue
把验收结果变成 bounty / contribution shares / contribution record
把项目收入变成可 claim 的周期分配
```

## 2. 用户第一屏

Project Room 首屏只显示 5 个动作。

```text
Subscribe / Pay
Pick Work
Submit Result
Review
Claim
```

对应用户语言：

```text
订阅项目
领取任务
提交结果
验收结果
领取收入
```

首屏模块：

```text
Project Revenue Address
Available Work
Review Queue
My Rewards
Contribution Record
```

`My Rewards` 显示：

```text
已获得 contribution shares
本期可领取 USDC
钱包绑定状态
Claim 按钮
```

## 3. 最小闭环

```text
Maintainer imports GitHub issue
  -> creates Work Thread
  -> sets Task Value and bounty
  -> Contributor handles with OpenClaw
  -> OpenClaw writes Result.md
  -> monopolyfun submit
  -> Reviewer accepts / resubmits / rejects
  -> accepted creates contribution record
  -> bounty settles
  -> contribution shares enter next distribution snapshot
  -> member claims USDC
```

成功标准：

```text
Maintainer 能把 issue 发布成任务。
Contributor 能用 OpenClaw 完成任务并提交 Result.md。
Reviewer 能用三个按钮完成验收。
Contributor 验收通过后立刻看到 bounty、shares、claim 入口。
Project Room 能展示项目收款地址和贡献分配记录。
```

## 4. Work Thread

`Work Thread` 是唯一用户可见任务对象。

发布任务只填 5 个字段：

```text
Goal
Deliverables
Acceptance Criteria
Task Value
Bounty
```

`Task Value` 是 0-10000 的任务价值分，用来计算 contribution shares。

```text
100-300
杂项修正。错别字、链接失效、按钮文案、简单样式偏差。

500
轻量任务。空状态、错误提示、README 段落、简单校验。

1000
标准任务。真实 bug、关键测试、清晰页面状态优化。

2000
有效改进。Result.md 最小提交校验、Review Queue 展示字段整理。

3500
关键流程。Work Thread -> Submit -> Review Queue 跑通。

5000
核心能力。OpenClaw pull / submit CLI 跑通，验收后生成 contribution record。

7500
商业闭环。GitHub issue -> OpenClaw PR -> Result.md -> Review -> bounty / shares settlement。

10000
核心突破。GitHub issue 自动变成可领取任务，PR 验收后结算现金和项目贡献份额。
```

Work Thread packet：

```md
---
contractVersion: 1.0
packetType: work_thread
projectNo: MF260525PRJ000001X
workThreadId: wt_123
runtime: openclaw
taskValue: 3500
bounty: 80 USDC
---

# Work Thread

## Goal
优化登录失败提示。

## Deliverables
- PR 链接
- 测试结果说明
- 变更文件清单

## Acceptance Criteria
- 用户能看到具体失败原因
- 文案支持中英文
- auth error case 测试通过

## Context
- Repo: github.com/org/app
- Related issue: GH-1842
```

## 5. OpenClaw 本地执行

网页按钮：

```text
领取并用 OpenClaw 处理
```

复制命令：

```bash
openclaw "继续 MonopolyFun work wt_123"
```

OpenClaw Skill 执行：

```bash
monopolyfun pull wt_123
```

本地只生成两个文件：

```text
.monopolyfun/
  work-thread.md
  result.md
```

第一版 CLI 只保留三条命令：

```bash
monopolyfun pull wt_123
monopolyfun status
monopolyfun submit
```

## 6. Result.md

OpenClaw 完成后写 `result.md`，用户确认后提交。

```md
---
contractVersion: 1.0
packetType: work_result
projectNo: MF260525PRJ000001X
workThreadId: wt_123
runtime: openclaw
---

# Result

## Summary
完成登录失败提示优化，错误提示现在能显示具体失败原因。

## Evidence
- PR: https://github.com/org/app/pull/1842
- Test: pnpm test passed

## Changed Files
- apps/web/login/page.tsx
- apps/web/messages/zh-CN/auth.json
- tests/login.spec.ts

## Notes
保留原登录流程，只调整错误码映射和提示文案。
```

PR bounty 的最小提交校验：

```text
result.md 存在
Summary 有内容
PR 链接存在
Test 说明存在
Changed Files 有内容
frontmatter 匹配 projectNo / workThreadId
Work Thread 状态允许提交
```

## 7. Review 状态机

状态机：

```text
open
  -> running
  -> submitted
  -> accepted
  -> settled
```

Review 分支：

```text
submitted
  -> running
  -> submitted

submitted
  -> rejected
```

Reviewer 只有三个按钮：

```text
通过
重新提交
拒绝
```

规则：

```text
通过
  -> 生成 contribution record
  -> 生成 bounty settlement
  -> 计入 contribution shares

重新提交
  -> Reviewer 写一句可执行修改说明
  -> Work Thread 回到 running
  -> 同一个任务最多两轮

拒绝
  -> Reviewer 写明未满足的 Acceptance Criteria
  -> Work Thread 关闭
```

## 8. Contribution Shares

V1 使用周期固定贡献池，Task Value 表示贡献权重。

```text
periodMintBudget = 10,000 shares
periodAcceptedTaskValueTotal = 本周期通过任务的 Task Value 总和

taskShares =
  periodMintBudget * taskValue / periodAcceptedTaskValueTotal
```

例子：

```text
本月贡献池：10,000 shares
本月通过任务总价值：50,000
某任务 Task Value：5,000

taskShares = 10,000 * 5,000 / 50,000 = 1,000 shares
```

收益口径：

```text
bounty
  -> accepted 后立即结算

contribution shares
  -> 进入下一个 distribution snapshot
  -> 按周期领取项目收入
```

Contributor 验收通过页显示：

```text
你的贡献已被接受
获得 bounty: 80 USDC
获得 contribution shares: 1,000
本期可 claim: 12 USDC
贡献记录已写入 Project Room
```

## 9. 项目收入地址与 Claim

每个 Project 设置一个链上收款地址，用来接收用户订阅或项目服务收入。

```text
Subscriber
  -> pays USDC
  -> Project Revenue Address
  -> Distribution Batch
  -> Member Claim
  -> Member Wallet
```

V1 使用项目级合约：

```text
projectRevenueAddress = RevenueDistributor contract
acceptedToken = USDC
```

合约只保留三个动作：

```text
pay(amount)
setDistributionRoot(period, root, totalAmount)
claim(period, amount, proof)
```

付款事件记录：

```text
projectId
payer
token
amount
txHash
period
```

Distribution Batch 由后端生成：

```text
Distribution Period: 2026-05
Total Revenue: 1,000 USDC
Total Shares: 100,000
Share Snapshot: period close
Wallet Snapshot: period close
Merkle Root: root
```

成员可领取金额：

```text
claimable = totalRevenue * memberSnapshotShares / totalSnapshotShares
```

例子：

```text
Alice shares = 12,000
Total shares = 100,000
Revenue = 1,000 USDC

Alice claimable = 120 USDC
```

Claim proof 绑定字段：

```text
chainId
contract
token
projectId
period
memberWalletAddress
amount
distributionRoot
```

真实 USDC claim 进入灰度发布。上线门槛：

```text
钱包绑定
地区校验
成员身份确认
税务信息
制裁筛查
项目方确认 distribution batch
```

## 10. 用户可见对象

```text
Project Room
Work Thread
Result
Review
Bounty
Contribution Shares
Project Revenue Address
Distribution
Claim
Contribution Record
Continuation Note
```

内部对象：

```text
validation task
work receipt
review decision
shares ledger
distribution batch
claim record
```

## 11. 后端改造文档

后端第一阶段改造单独维护在：

```text
docs/product/openclaw-bounty-backend-mvp.md
```

主文只保留后端边界：

```text
Work Thread 是现有 WorkItem 的对外命名层。
Result.md 写入 work_receipts.output。
Review 复用 work_reviews。
Contribution shares 复用 shares_ledger。
Revenue claim 使用独立 distribution batch / claim record。
```

## 12. 最小接口

复用现有 validation task / work item / proof / review / reward 体系，`Work Thread` 只是对外命名层。

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

## 13. V1 暂缓项

```text
自动 git diff 采集
自动 PR / CI 状态采集
自动截图
复杂 evidence 目录
多 harness 泛化
自动下一步生成
外部资金买入 bonding curve
二级市场
复杂治理投票
```

## 14. GTM 实验

两周 concierge MVP：

```text
5 个真实 GitHub repo
每个 repo 导入 3 个 issue
每个 issue 设置 Task Value 和 bounty
OpenClaw 完成 PR
Reviewer 验收 Result.md
MonopolyFun 记录 bounty / shares / claim
```

核心指标：

```text
Issue-to-WorkThread 转化率
Pull-to-Submit 完成率
Submitted-to-Accepted 验收率
Accepted-to-Settlement 结算率
Accepted-to-Claim 领取率
14 天内第二个 Work Thread 发布率
```

产品主张：

```text
用 OpenClaw 修真实 GitHub issue，拿 bounty，也拿项目贡献份额。
```
