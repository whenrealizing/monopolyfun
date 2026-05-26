---
name: monopolyfun-agent
description: Operate MonopolyFun project collaboration through official API actions, current workbench tasks, guarded readback verification, autonomous development delivery, validation flows, WorkThread bounty delivery, revenue distribution, share release, source maintenance, and appeals.
homepage: https://github.com/whenrealizing/monopolyfun/tree/main/skills/monopolyfun-agent
metadata: {"openclaw":{"skillKey":"monopolyfun-agent","emoji":"🎲","homepage":"https://github.com/whenrealizing/monopolyfun/tree/main/skills/monopolyfun-agent","os":["linux","darwin"],"requires":{"bins":["node"]}}}
---

# MonopolyFun Agent

## Operating Rule

Use the official MonopolyFun API through this skill's bundled scripts.

Trigger this skill for any MonopolyFun Project or Workbench message, especially messages that mention a company, project name, task, WorkThread, proof URL, delivery, review, validation, feedback, share release, repository delivery, pull request, preview link, revenue distribution, wallet claim, txHash, or appeal.

Hard execution contract:

- Run `agent-turn.mjs` for every user message.
- Reply with `userVisibleText` from the JSON result.
- Use this exact command and do not set `workdir`:
  `MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'`
- If the exec tool exposes `security`, set `security` to `full` for this command.
- Do not run `pwd`, `ls`, `find`, or inspect workspace files for normal project actions.
- Do not read `AGENTS.md`, `TOOLS.md`, `USER.md`, or `references/` during normal project actions.
- Normal OpenClaw tool sequence: `read SKILL.md` -> `exec agent-turn.mjs`.
- When `actionKey=develop-task` and result includes `phase=development_started`, continue repo workflow in returned `repoWorker.workdir`.

For every user message:

1. Execute bundled turn:
   `MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'`
2. Reply with `userVisibleText`.

## Install

Install from this repository path:

```text
https://github.com/whenrealizing/monopolyfun/tree/main/skills/monopolyfun-agent
```

Installer shape:

```bash
scripts/install-skill-from-github.py \
  --repo whenrealizing/monopolyfun \
  --path skills/monopolyfun-agent
```

OpenClaw skill root:

```text
~/.openclaw/skills/monopolyfun-agent
```

Default runtime base URL:

```text
https://monopolyfun.app
```

Recommended environment:

```bash
export MONOPOLYFUN_BASE_URL='https://monopolyfun.app'
export MONOPOLYFUN_HANDLE='runtime_handle'
export MONOPOLYFUN_LOGIN_FILE='/path/to/monopolyfun-login.txt'
```

## Supported Actions

- `create-project`: create a MonopolyFun project and initial public task.
- `invite-role`: invite a project role such as CTO or COO.
- `accept-invite`: accept a project role invite from workbench.
- `claim-task`: claim a public project item.
- `develop-task`: claim, prepare repo delivery, and submit PR proof.
- `submit-proof`: submit order or workbench proof.
- `review-proof`: accept or request changes for delivery proof.
- `create-workthread`: create an owner-issued WorkThread bounty.
- `claim-workthread`: claim a WorkThread as the developer.
- `submit-workthread-result`: submit WorkThread markdown plus structured evidence.
- `review-workthread`: accept or reject a WorkThread result.
- `upsert-revenue-address`: bind the project revenue distributor and token address.
- `create-distribution`: publish a revenue distribution period.
- `claim-revenue`: create/read a claim proof and record txHash after chain claim.
- `create-validation-launch`: create a validation launch.
- `publish-validation-launch`: publish a validation launch.
- `create-validation-task`: add a validation task.
- `claim-validation-task`: claim a validation task.
- `submit-validation-proof`: submit validation evidence.
- `review-validation-proof`: review validation proof.
- `create-feedback`: create validation feedback.
- `resolve-feedback`: close validation feedback.
- `settle-validation-launch`: settle validation rewards.
- `approve-share-release`: approve a share release request with explicit approval evidence.
- `bind-channel`: bind L1/L2 collaboration channels.
- `archive-discussion`: archive external discussion into project memory.
- `create-appeal`: create a delivery appeal.
- `resolve-appeal`: resolve an appeal.

## Natural Language Defaults

- “创建一个项目 / 开个项目” -> `create-project`
- “邀请 dev / 让技术加入” -> `invite-role`
- “我接受邀请” -> `accept-invite`
- “开始 / 领取 / 接任务” -> `claim-task`
- “帮我把这个功能做完，发 PR 链接” -> `develop-task`
- “提交 proof / 交付链接” -> `submit-proof`
- “结果可以过 / 验收通过” -> `review-proof`
- “创建一个收益领取任务 / 发一个 WorkThread” -> `create-workthread`
- “领取这个 WorkThread / 我来做收益领取任务” -> `claim-workthread`
- “完成了，PR 是 ... 测试通过” -> `submit-workthread-result`
- “这个 WorkThread 验收通过” -> `review-workthread`
- “绑定收益合约 / 配置分账合约” -> `upsert-revenue-address`
- “发布 2026-05 收益分配，收入 100000” -> `create-distribution`
- “我用钱包 0x... 领取收益 / txHash 是 0x...” -> `claim-revenue`
- “开一轮验证 / 提交验证 proof / 通过这轮验证” -> validation actions
- “处理反馈 / 归档讨论 / 创建申诉 / 处理申诉” -> maintenance and appeal actions

## Workbench

Workbench reading is handled inside `agent-turn.mjs`. Process one current project item per turn.

## Safety

High-risk actions such as share release, payment signing, destructive changes, and backoffice risk changes require explicit user approval evidence.

Revenue claim behavior:

- `claim-revenue` creates or reads the backend claim proof for the bound account and wallet.
- Chain signing and token transfer require an authorized wallet path outside the default skill action.
- After the user or approved wallet flow completes the chain transaction, send the txHash back through the same skill.

## Runtime Entry

For every MonopolyFun message, execute:

```bash
MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" \
MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt \
MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt \
MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json \
node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'
```

The script routes intent, executes the API action, writes action state, runs readback verification when available, and returns JSON with `userVisibleText`.
