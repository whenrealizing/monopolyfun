#!/usr/bin/env node

import {
  DEFAULT_BASE_URL,
  DEFAULT_HANDLE_FILE,
  DEFAULT_LOGIN_FILE,
  DEFAULT_SESSION_CACHE_FILE,
  apiJson,
  buildFailurePayload,
  parseArgs,
  printJson,
  readOption,
  resolveRuntimeAuth,
} from "./runtime-session.mjs";
import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createHash, randomUUID } from "node:crypto";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const DEFAULT_STATE_FILE = join(homedir(), ".openclaw", "monopolyfun", "action-state.json");
const PROJECT_NO_PATTERN = /MF\d+PRJ[0-9A-Z]+/i;

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write([
    "usage: node scripts/execute-action.mjs --action create-project --params '{\"title\":\"QA\"}'",
    "actions:",
    "  create-project, invite-role, accept-invite, claim-task, develop-task, submit-proof, review-proof",
    "  create-workthread, claim-workthread, submit-workthread-result, review-workthread",
    "  upsert-revenue-address, create-distribution, claim-revenue",
    "  create-validation-launch, publish-validation-launch, create-validation-task",
    "  claim-validation-task, submit-validation-proof, review-validation-proof",
    "  approve-share-release, bind-channel, archive-discussion, create-appeal, resolve-appeal",
    "",
  ].join("\n") + "\n");
  process.exit(0);
}

try {
  const action = readOption(flags, "action", { required: true });
  const params = parseJsonOption(readOption(flags, "params", { defaultValue: "{}" }), "params");
  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: DEFAULT_BASE_URL,
  });
  const stateFile = readOption(flags, "state-file", {
    envKeys: ["MONOPOLYFUN_ACTION_STATE_FILE"],
    defaultValue: DEFAULT_STATE_FILE,
  });
  const runtime = await resolveRuntimeAuth({
    baseUrl,
    handle: readOption(flags, "handle", { envKeys: ["MONOPOLYFUN_HANDLE"] }),
    handleFile: readOption(flags, "handle-file", {
      envKeys: ["MONOPOLYFUN_HANDLE_FILE"],
      defaultValue: DEFAULT_HANDLE_FILE,
    }),
    password: readOption(flags, "password", {
      envKeys: ["MONOPOLYFUN_PASSWORD", "MONOPOLYFUN_LOGIN_SECRET", "MONOPOLYFUN_LOGIN_VALUE"],
    }),
    loginFile: readOption(flags, "login-file", {
      envKeys: ["MONOPOLYFUN_LOGIN_FILE"],
      defaultValue: DEFAULT_LOGIN_FILE,
    }),
    sessionCacheFile: readOption(flags, "session-cache-file", {
      envKeys: ["MONOPOLYFUN_SESSION_CACHE_FILE"],
      defaultValue: DEFAULT_SESSION_CACHE_FILE,
    }),
    cookieHeader: readOption(flags, "cookie", { envKeys: ["MONOPOLYFUN_COOKIE"] }),
    csrfToken: readOption(flags, "csrf", { envKeys: ["MONOPOLYFUN_CSRF"] }),
  });
  const client = { baseUrl, session: runtime.session, account: runtime.account ?? await currentAccount(runtime.session, baseUrl) };
  const state = await readState(stateFile);
  const result = await execute(action, mergeParams(state, params), client);
  const nextState = nextStateFor(action, state, result);
  await writeState(stateFile, nextState);
  printJson({ status: "ok", action, result, state: nextState });
} catch (error) {
  printJson(buildFailurePayload(error, { phase: "execute_action" }));
  process.exit(1);
}

async function execute(action, params, client) {
  switch (action) {
    case "create-project":
      return createProject(params, client);
    case "invite-role":
      return inviteRole(params, client);
    case "accept-invite":
      return runWorkbenchAction(params, client, "accept_project_invite");
    case "claim-task":
      return claimProjectTask(params, client);
    case "develop-task":
      return developProjectTask(params, client);
    case "submit-proof":
      return submitProjectProof(params, client);
    case "review-proof":
      if (isRevisionDecision(params.decision)) {
        return runWorkbenchAction(params, client, "revise_receipt", {
          reviewerAccountId: client.account.id,
          reason: params.reason || "需要补充后再次验收。",
          evidenceRefs: params.evidenceRefs || [],
        });
      }
      return acceptProjectDelivery(params, client);
    case "create-workthread":
      return createWorkThread(params, client);
    case "claim-workthread":
      return claimWorkThread(params, client);
    case "submit-workthread-result":
      return submitWorkThreadResult(params, client);
    case "review-workthread":
      return reviewWorkThread(params, client);
    case "upsert-revenue-address":
      return upsertRevenueAddress(params, client);
    case "create-distribution":
      return createDistribution(params, client);
    case "claim-revenue":
      return claimRevenue(params, client);
    case "create-validation-launch":
      return createValidationLaunch(params, client);
    case "publish-validation-launch":
      return publishValidationLaunch(params, client);
    case "create-validation-task":
      return createValidationTask(params, client);
    case "claim-validation-task":
      return claimValidationTask(params, client);
    case "submit-validation-proof":
      return submitValidationProof(params, client);
    case "review-validation-proof":
      return reviewValidationProof(params, client);
    case "create-feedback":
      return createValidationFeedback(params, client);
    case "resolve-feedback":
      return resolveValidationFeedback(params, client);
    case "settle-validation-launch":
      return settleValidationLaunch(params, client);
    case "approve-share-release":
      return runWorkbenchAction(params, client, "approve_share_release", {
        approverAccountId: client.account.id,
        reason: params.reason || "审批 shares 发放。",
      });
    case "bind-channel":
      return bindChannel(params, client);
    case "archive-discussion":
      return archiveDiscussion(params, client);
    case "create-appeal":
      return createAppeal(params, client);
    case "resolve-appeal":
      return resolveAppeal(params, client);
    default:
      throw new Error(`unsupported action: ${action}`);
  }
}

async function createProject(params, client) {
  const title = required(params.title, "title");
  const taskName = params.taskName || "QA 验证任务";
  const body = {
    title,
    description: params.description || `${title} 的项目协作验收流。`,
    goal: params.goal || "完成从项目创建、邀请、领取、提交 proof、验收、归档和申诉处理的协作闭环。",
    ownerIntro: params.ownerIntro || "由 MonopolyFun agent 初始化项目协作流程。",
    ...(params.provisionSessionId ? { provisionSessionId: params.provisionSessionId } : {}),
    items: Array.isArray(params.items) && params.items.length > 0 ? params.items : [{
      name: taskName,
      description: "验证项目协作工作流可以被领取、提交 proof 并完成验收。",
      deliveryStandard: "提交真实 proof 链接，验收方可以读取并确认结果。",
      acceptanceCriteria: ["任务可以被领取", "proof 链接可以读取", "验收结果可以读回"],
      difficultyScore: 1,
    }],
  };
  // 中文注释：创建项目走正式 publishProject API，同时 includeAgent 便于下一步直接拿到 agent 能力摘要。
  const created = await apiJson(client.session, client.baseUrl, "POST", "/api/v1/projects?includeAgent=true", body);
  const projectNo = String(created?.project?.projectNo ?? created?.projectNo ?? "");
  if (!projectNo) {
    throw new Error("create project response missing projectNo");
  }
  // 中文注释：最新 Project 读模型以 dashboard.workspace.items 承接初始任务，旧 ledger 端点已退出公开验收面。
  const dashboard = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  return { projectNo, project: created.project ?? created, dashboard: summarizeProjectDashboard(dashboard) };
}

async function inviteRole(params, client) {
  const projectNo = requiredProjectNo(params);
  const roleCode = params.roleCode || "system_cto";
  const accountId = params.accountId || await resolveAccountId(params.account, client);
  const body = {
    accountId,
    message: params.message || `邀请你以 ${roleCode} 角色参与 ${projectNo}。`,
  };
  const invited = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectNo)}/roles/${encodeURIComponent(roleCode)}/invite`, body);
  return { projectNo, roleCode, accountId, invited };
}

async function claimProjectTask(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const dashboard = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  const item = firstOpenProjectItem(dashboard, params.taskQuery);
  if (!item?.id) {
    throw new Error(`open project item not found for ${projectNo}`);
  }
  // 中文注释：普通 Project 的最新领取入口是公开项目 item claim，领取后订单与 worker workbench 由后端生成。
  const receipt = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/items/${encodeURIComponent(item.id)}/claim`, {
    actorAccountId: client.account.id,
    buyerNote: params.buyerNote || "claimed by OpenClaw agent",
    paymentRecipient: params.paymentRecipient || "",
    deliveryInput: params.deliveryInput || { agentRuntime: "openclaw" },
  });
  const readback = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  return { projectNo, item: summarizeProjectItem(item), receipt, dashboard: summarizeProjectDashboard(readback) };
}

async function developProjectTask(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const taskQuery = params.taskQuery || params.userRequest || "";
  let dashboard = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  let item = activeProjectItem(dashboard, taskQuery);
  let claim = null;
  if (!item?.activeOrderNo) {
    const openItem = firstOpenProjectItem(dashboard, taskQuery);
    if (!openItem?.id) {
      return developBlocked(projectNo, "task_not_found", params, {
        message: "I found the project, and I need a visible open task before I can start development.",
        dashboard: summarizeProjectDashboard(dashboard),
      });
    }
    // 中文注释：自主开发从领取公开 item 开始，确保后续 repo delivery session 绑定真实 order。
    claim = await claimProjectTask({ ...params, projectNo, taskQuery }, client);
    dashboard = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
    item = activeProjectItem(dashboard, taskQuery);
  }
  if (!item?.activeOrderNo) {
    return developBlocked(projectNo, "order_not_ready", params, {
      message: "I claimed the task, and the order has not appeared in the project readback yet.",
      dashboard: summarizeProjectDashboard(dashboard),
      claim,
    });
  }

  let session;
  try {
    session = await createRepoDeliverySession(projectNo, item.activeOrderNo, client);
  } catch (error) {
    return developBlocked(projectNo, "repo_delivery_unavailable", params, {
      message: readableError(error),
      dashboard: summarizeProjectDashboard(dashboard),
      item: summarizeProjectItem(item),
      claim,
    });
  }

  const prUrl = params.prUrl || params.pullRequestUrl || "";
  const headCommit = params.headCommit || params.commitSha || "";
  if (!prUrl || !headCommit) {
    const worker = await prepareRepoWorkspace(session, params);
    if (worker.status !== "ok") {
      return developBlocked(projectNo, "repo_workspace_unavailable", params, {
        message: worker.message || JSON.stringify(worker),
        dashboard: summarizeProjectDashboard(dashboard),
        item: summarizeProjectItem(item),
        claim,
        repoDelivery: summarizeRepoDelivery(session),
      });
    }
    return {
      projectNo,
      phase: "development_started",
      proactive: proactiveEvents(projectNo, [
        ["task_found", `I found the current task for ${params.projectQuery || projectNo}.`],
        ["task_claimed", `I claimed the task ${item.title || item.id}.`],
        ["development_started", "I started the repository delivery session. I will report the PR link when the code is pushed."],
      ]),
      item: summarizeProjectItem(item),
      claim,
      repoDelivery: summarizeRepoDelivery(session),
      repoWorker: worker,
      dashboard: summarizeProjectDashboard(dashboard),
    };
  }

  let reported;
  try {
    reported = await apiJson(client.session, client.baseUrl, "POST",
      `/api/v1/work/repo-delivery-sessions/${encodeURIComponent(session.deliverySessionId)}/report-pr`, {
        prUrl,
        headCommit,
        diffSummary: params.diffSummary || params.summary || params.userRequest || "OpenClaw completed the requested code changes.",
      });
  } catch (error) {
    if (isCiPendingError(error)) {
      return {
        projectNo,
        phase: "pull_request_waiting_for_ci",
        proactive: proactiveEvents(projectNo, [
          ["task_found", `I found the current task for ${params.projectQuery || projectNo}.`],
          ["pull_request_ready", `I pushed the PR and will keep watching CI: ${prUrl}`],
          ["ci_waiting", "The PR is waiting for CI. I will submit proof after the checks are ready."],
        ]),
        item: summarizeProjectItem(item),
        claim,
        repoDelivery: summarizeRepoDelivery(session),
        pullRequest: { prUrl, headCommit },
        dashboard: summarizeProjectDashboard(dashboard),
      };
    }
    throw error;
  }
  const artifacts = await repoProofArtifacts(session, reported, params, client);
  const finalized = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/work/repo-delivery-sessions/${encodeURIComponent(session.deliverySessionId)}/finalize-proof`, {
      summary: params.summary || "OpenClaw completed code changes, pushed a PR, and submitted proof.",
      artifacts,
      criteriaRefs: params.criteriaRefs || [],
      evidenceRefs: params.evidenceRefs || [prUrl, headCommit],
    });
  return {
    projectNo,
    phase: "delivery_ready",
    proactive: proactiveEvents(projectNo, [
      ["task_found", `I found the current task for ${params.projectQuery || projectNo}.`],
      ["task_claimed", `I claimed the task ${item.title || item.id}.`],
      ["delivery_ready", `I pushed the PR and submitted proof: ${prUrl}`],
      ["acceptance_needed", "Please open the link and confirm the result. I can then finish the acceptance step."],
    ]),
    item: summarizeProjectItem(item),
    claim,
    repoDelivery: summarizeRepoDelivery(reported),
    artifacts,
    finalized,
    dashboard: summarizeProjectDashboard(dashboard),
  };
}

async function acceptProjectDelivery(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const dashboard = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  const item = activeProjectItem(dashboard);
  if (!item?.activeOrderNo) {
    return runWorkbenchAction(params, client, "review_receipt", {
      reviewerAccountId: client.account.id,
      decision: normalizeReviewDecision(params.decision),
      reason: params.reason || "验收通过。",
      evidenceRefs: params.evidenceRefs || [],
    });
  }
  // 中文注释：普通 Project 的交付验收由项目 owner/请求方接受 active order，workbench review 只覆盖专门的工作项。
  const receipt = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/work/orders/${encodeURIComponent(item.activeOrderNo)}/accept`, {
    acceptedByAccountId: client.account.id,
    note: params.reason || "验收通过。",
  });
  const readback = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  return { projectNo, item: summarizeProjectItem(item), receipt, dashboard: summarizeProjectDashboard(readback) };
}

async function submitProjectProof(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const dashboard = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  const item = activeProjectItem(dashboard);
  if (!item?.activeOrderNo) {
    return runWorkbenchAction(params, client, "submit_receipt", submitReceiptInput(params, client));
  }
  const proofUrl = params.proofUrl || params.url || "";
  const criteriaRefs = normalizeCriteriaRefs(params.criteriaRefs, item.acceptanceCriteria);
  // 中文注释：公开 Project item claim 后的交付入口是 order proof API，workbench submit_receipt 只覆盖内部工作项。
  const receipt = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/work/orders/${encodeURIComponent(item.activeOrderNo)}/proofs`, {
    submittedByAccountId: client.account.id,
    summary: params.summary || `proof 已提交：${proofUrl}`,
    links: proofUrl ? [{ label: "proof", href: proofUrl }] : [],
    artifacts: proofUrl ? [proofUrl] : [],
    proofPayload: { agentRuntime: "openclaw", proofUrl },
    executionMode: "AGENT",
    agentRuntime: "openclaw",
    evidenceRefs: proofUrl ? [proofUrl] : [],
    criteriaRefs,
    visibility: "public",
  });
  const readback = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  return { projectNo, item: summarizeProjectItem(item), receipt, dashboard: summarizeProjectDashboard(readback) };
}

async function createWorkThread(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const projectId = await resolveProjectId({ ...params, projectNo }, client);
  const body = {
    actorAccountId: client.account.id,
    title: params.title || "OpenClaw 收益领取验证任务",
    goal: params.goal || params.summary || "完成可复核交付，并进入贡献收益分配。",
    deliverables: normalizeList(params.deliverables, ["PR 或执行报告", "测试摘要", "收益领取证据"]),
    acceptanceCriteria: normalizeList(params.acceptanceCriteria, ["结果可以复核", "收益领取证据可以读回"]),
    taskValue: Number.isFinite(Number(params.taskValue)) ? Number(params.taskValue) : 5000,
    bountyAmountMinor: Number.isFinite(Number(params.bountyAmountMinor)) ? Number(params.bountyAmountMinor) : 0,
    bountyToken: params.bountyToken || "BNB",
    repoRef: params.repoRef || "",
    issueUrl: params.issueUrl || params.proofUrl || "",
    reviewerAccountId: params.reviewerAccountId || "",
  };
  // 中文注释：WorkThread 使用项目内部 id 写入，返回 projectNo 供 OpenClaw 后续自然语言继续沿用。
  const workThread = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectId)}/work-threads`, body);
  const workroom = await readWorkroom(client, projectId);
  return { projectNo, projectId, workThread, workroom: summarizeWorkroom(workroom) };
}

async function claimWorkThread(params, client) {
  const locator = await resolveWorkThreadLocator(params, client, ["open"]);
  // 中文注释：领取时显式传入当前账号，后端会拒绝 owner 自领和重复领取，避免 agent 伪造收益资格。
  const receipt = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/work-threads/${encodeURIComponent(locator.workThreadId)}/claim`, {
      actorAccountId: client.account.id,
      runtime: params.runtime || "openclaw",
    });
  const packet = await apiJson(client.session, client.baseUrl, "GET",
    `/api/v1/work-threads/${encodeURIComponent(locator.workThreadId)}/packet`);
  return { ...locator, receipt, packet: summarizeWorkThreadPacket(packet) };
}

async function submitWorkThreadResult(params, client) {
  const locator = await resolveWorkThreadLocator(params, client, ["running", "open"]);
  const resultMarkdown = params.resultMarkdown || buildWorkThreadResultMarkdown(locator.workThreadId, params);
  const body = {
    actorAccountId: client.account.id,
    resultMarkdown,
    summary: params.summary || "OpenClaw 已完成 WorkThread 交付。",
    prUrl: params.prUrl || params.proofUrl || "https://github.com/monopolyfun/openclaw-smoke/pull/1",
    testSummary: params.testSummary || "OpenClaw smoke 已完成可复核检查。",
    changedFiles: normalizeList(params.changedFiles, ["docs/evidence/openclaw-smoke/result.md"]),
    evidenceRefs: normalizeList(params.evidenceRefs, [params.prUrl || params.proofUrl || "https://github.com/monopolyfun/openclaw-smoke/pull/1"]),
    runtime: params.runtime || "openclaw",
  };
  // 中文注释：结构化字段和 markdown 同时提交，便于人类验收和机器读回保持同一份证据。
  const result = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/work-threads/${encodeURIComponent(locator.workThreadId)}/result`, body);
  return { ...locator, result };
}

async function reviewWorkThread(params, client) {
  const locator = await resolveWorkThreadLocator(params, client, ["submitted", "running", "open"]);
  const review = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/work-threads/${encodeURIComponent(locator.workThreadId)}/review`, {
      reviewerAccountId: client.account.id,
      decision: normalizeWorkThreadDecision(params.decision),
      reason: params.reason || "交付结果已复核。",
    });
  const workroom = await readWorkroom(client, locator.projectId);
  return { ...locator, review, workroom: summarizeWorkroom(workroom) };
}

async function upsertRevenueAddress(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const projectId = await resolveProjectId({ ...params, projectNo }, client);
  const address = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectId)}/revenue-address`, {
      actorAccountId: client.account.id,
      chainId: required(params.chainId, "chainId"),
      contractAddress: required(params.contractAddress, "contractAddress"),
      tokenAddress: required(params.tokenAddress, "tokenAddress"),
    });
  return { projectNo, projectId, revenueAddress: address };
}

async function createDistribution(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const projectId = await resolveProjectId({ ...params, projectNo }, client);
  const period = params.period || currentPeriod();
  const distribution = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectId)}/distributions`, {
      actorAccountId: client.account.id,
      period,
      totalRevenueMinor: Number(required(params.totalRevenueMinor, "totalRevenueMinor")),
    });
  const workroom = await readWorkroom(client, projectId);
  return { projectNo, projectId, period, distribution, workroom: summarizeWorkroom(workroom) };
}

async function claimRevenue(params, client) {
  const projectNo = await resolveProjectNo(params, client);
  const projectId = await resolveProjectId({ ...params, projectNo }, client);
  const period = params.period || await latestDistributionPeriod(client, projectId) || currentPeriod();
  // 中文注释：txHash 回填阶段允许省略钱包地址，后端会沿用首次 claim 固定的钱包。
  const walletAddress = String(params.walletAddress || "").trim();
  const claim = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectId)}/distributions/${encodeURIComponent(period)}/claim`, {
      actorAccountId: client.account.id,
      walletAddress,
      txHash: params.txHash || "",
      txConfirmed: params.txConfirmed === true,
    });
  // 中文注释：这里只记录后端 claim/txHash，链上签名仍由用户或被授权的钱包流程完成。
  return { projectNo, projectId, period, walletAddress, claim };
}

function firstOpenProjectItem(dashboard, taskQuery = "") {
  const items = Array.isArray(dashboard?.workspace?.items) ? dashboard.workspace.items : [];
  const matches = taskQuery ? items.filter((item) => itemMatchesQuery(item, taskQuery)) : items;
  return matches.find((item) => item.status === "open" && !item.claimedByAccountId)
    ?? items.find((item) => item.status === "open" && !item.claimedByAccountId)
    ?? null;
}

function activeProjectItem(dashboard, taskQuery = "") {
  const items = Array.isArray(dashboard?.workspace?.items) ? dashboard.workspace.items : [];
  const matches = taskQuery ? items.filter((item) => itemMatchesQuery(item, taskQuery)) : items;
  return matches.find((item) => item.activeOrderNo)
    ?? items.find((item) => item.activeOrderNo)
    ?? null;
}

function itemMatchesQuery(item, query) {
  return JSON.stringify(item).toLowerCase().includes(String(query || "").toLowerCase());
}

async function createRepoDeliverySession(projectNo, orderNo, client) {
  return await apiJson(client.session, client.baseUrl, "POST", "/api/v1/work/repo-delivery-sessions", {
    projectNo,
    orderNo,
    runtime: "openclaw",
  });
}

function developBlocked(projectNo, reason, params, details = {}) {
  return {
    projectNo,
    phase: "blocked_need_user",
    reason,
    proactive: proactiveEvents(projectNo, [
      ["task_found", `I checked ${params.projectQuery || projectNo}.`],
      ["blocked_need_user", details.message || "I need one user-visible detail before I can continue."],
      ["closed_with_recap", `Blocked at ${reason}.`],
    ]),
    ...details,
  };
}

function proactiveEvents(projectNo, entries) {
  return entries.map(([kind, message]) => ({
    kind,
    projectNo,
    message,
    createdAt: new Date().toISOString(),
  }));
}

function summarizeRepoDelivery(session) {
  return {
    deliverySessionId: session.deliverySessionId,
    projectNo: session.projectNo,
    orderNo: session.orderNo,
    repoUrl: session.repoUrl,
    cloneUrlPresent: Boolean(session.cloneUrl),
    baseBranch: session.baseBranch,
    headBranch: session.headBranch,
    prUrl: session.prUrl,
    headCommit: session.headCommit,
    ciStatus: session.ciStatus,
    status: session.status,
    expiresAt: session.expiresAt,
  };
}

function isCiPendingError(error) {
  const message = `${error?.body?.message || ""} ${error?.message || ""}`.toLowerCase();
  return message.includes("pull request ci is not successful");
}

async function repoProofArtifacts(session, reported, params, client) {
  const explicit = Array.isArray(params.artifacts)
    ? params.artifacts.map((item) => String(item ?? "").trim()).filter(Boolean)
    : [];
  if (explicit.length > 0 && explicit.every((item) => item.startsWith("asset://"))) {
    return explicit;
  }
  const asset = await createRepoProofAsset(session, reported, params, client);
  return [asset.artifactRef];
}

async function createRepoProofAsset(session, reported, params, client) {
  const proofLines = [
    "OpenClaw repository delivery proof",
    `Project: ${reported.projectNo || session.projectNo}`,
    `Order: ${reported.orderNo || session.orderNo}`,
    `Pull request: ${reported.prUrl}`,
    `Head commit: ${reported.headCommit}`,
    `CI status: ${reported.ciStatus || "unknown"}`,
    params.summary || params.diffSummary || "OpenClaw completed the requested code changes.",
  ];
  const content = buildProofPdf(proofLines);
  const checksumSha256 = createHash("sha256").update(content).digest("hex");
  const contentType = "application/pdf";
  const presigned = await apiJson(client.session, client.baseUrl, "POST", "/api/v1/uploads/presign", {
    orderId: reported.orderNo || session.orderNo,
    filename: `openclaw-proof-${reported.orderNo || session.orderNo}.pdf`,
    contentType,
    contentLengthBytes: content.length,
    checksumSha256,
    purpose: "proof",
    visibility: "participants",
  });
  await uploadPresignedProof(presigned, content, contentType, checksumSha256);
  const completed = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/uploads/${encodeURIComponent(presigned.assetId)}/complete`, {
    contentType,
    contentLengthBytes: content.length,
    checksumSha256,
  });
  return {
    assetId: completed.assetId || presigned.assetId,
    artifactRef: completed.artifactRef || presigned.artifactRef,
  };
}

function buildProofPdf(lines) {
  const textOps = lines
    .map((line, index) => `${index === 0 ? "50 760 Td" : "0 -22 Td"} (${escapePdfText(line).slice(0, 150)}) Tj`)
    .join("\n");
  const stream = `BT\n/F1 12 Tf\n${textOps}\nET`;
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    `<< /Length ${Buffer.byteLength(stream, "utf8")} >>\nstream\n${stream}\nendstream`,
  ];
  let pdf = "%PDF-1.4\n";
  const offsets = [0];
  for (let index = 0; index < objects.length; index += 1) {
    offsets.push(Buffer.byteLength(pdf, "utf8"));
    pdf += `${index + 1} 0 obj\n${objects[index]}\nendobj\n`;
  }
  const xrefOffset = Buffer.byteLength(pdf, "utf8");
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (let index = 1; index < offsets.length; index += 1) {
    pdf += `${String(offsets[index]).padStart(10, "0")} 00000 n \n`;
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  return Buffer.from(pdf, "utf8");
}

function escapePdfText(value) {
  return String(value ?? "")
    .replace(/[^\x20-\x7E]/g, "?")
    .replace(/\\/g, "\\\\")
    .replace(/\(/g, "\\(")
    .replace(/\)/g, "\\)");
}

async function uploadPresignedProof(presigned, content, contentType, checksumSha256) {
  const uploadUrl = String(presigned.uploadUrl || "");
  const isSignedObjectUrl = /[?&]X-Amz-Signature=/i.test(uploadUrl);
  // 中文注释：本地 fake upload provider 由 complete 阶段回放元数据，未签名 URL 无需真实 PUT。
  if (!uploadUrl || uploadUrl.includes("r2.example") || !isSignedObjectUrl) {
    return;
  }
  const headers = uploadHeaders(presigned, contentType, checksumSha256, isSignedObjectUrl);
  const response = await fetch(uploadUrl, {
    method: presigned.uploadMethod || "PUT",
    headers,
    body: content,
  });
  if (!response.ok) {
    const curlStatus = uploadPresignedProofWithCurl(uploadUrl, presigned, content, headers);
    if (curlStatus !== 0) {
      throw new Error(`proof artifact upload failed: ${response.status}`);
    }
  }
}

function uploadHeaders(presigned, contentType, checksumSha256, signedObjectUrl) {
  const headers = { ...(presigned.uploadHeaders || {}) };
  putHeaderIfAbsent(headers, "Content-Type", contentType);
  if (!signedObjectUrl) {
    putHeaderIfAbsent(headers, "x-upload-checksum-sha256", checksumSha256);
  }
  return headers;
}

function putHeaderIfAbsent(headers, name, value) {
  const exists = Object.keys(headers).some((existing) => existing.toLowerCase() === name.toLowerCase());
  if (!exists) {
    headers[name] = value;
  }
}

function uploadPresignedProofWithCurl(uploadUrl, presigned, content, headers) {
  const args = ["-fsS", "-X", presigned.uploadMethod || "PUT"];
  for (const [name, value] of Object.entries(headers)) {
    args.push("-H", `${name}: ${value}`);
  }
  args.push("--data-binary", "@-", uploadUrl);
  const result = spawnSync("curl", args, {
    input: content,
    encoding: "buffer",
    maxBuffer: 1024 * 1024,
  });
  return result.status ?? 1;
}

async function prepareRepoWorkspace(session, params) {
  if (params.prepareWorkspace === false || params.prepareWorkspace === "false") {
    return { status: "skipped", reason: "prepareWorkspace=false" };
  }
  const args = [
    join(dirname(fileURLToPath(import.meta.url)), "repo-delivery-worker.mjs"),
    "--mode",
    params.workerMode || (Array.isArray(params.commands) && params.commands.length > 0 ? "auto" : "prepare"),
    "--session-json",
    JSON.stringify(session),
  ];
  if (params.workspaceRoot) {
    args.push("--workspace-root", String(params.workspaceRoot));
  }
  if (params.workdir) {
    args.push("--workdir", String(params.workdir));
  }
  for (const command of Array.isArray(params.commands) ? params.commands : []) {
    args.push("--command", String(command));
  }
  for (const command of Array.isArray(params.checkCommands) ? params.checkCommands : []) {
    args.push("--check-command", String(command));
  }
  if (params.commitMessage) {
    args.push("--commit-message", String(params.commitMessage));
  }
  if (params.prTitle) {
    args.push("--pr-title", String(params.prTitle));
  }
  if (params.prBody) {
    args.push("--pr-body", String(params.prBody));
  }
  const result = spawnSync(process.execPath, args, {
    cwd: dirname(fileURLToPath(import.meta.url)),
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 20,
    env: process.env,
  });
  const payload = parseJson(result.stdout);
  if (result.status !== 0) {
    return {
      status: "blocked",
      message: payload?.message || result.stderr || result.stdout,
      payload,
    };
  }
  return payload || { status: "blocked", message: "repo delivery worker returned invalid JSON" };
}

function readableError(error) {
  const body = error?.body ? JSON.stringify(error.body) : "";
  return body || error?.message || String(error);
}

function normalizeCriteriaRefs(input, acceptanceCriteria) {
  const explicit = Array.isArray(input) ? input.map((item) => String(item ?? "").trim()).filter(Boolean) : [];
  if (explicit.length > 0) {
    return explicit;
  }
  const inherited = Array.isArray(acceptanceCriteria)
    ? acceptanceCriteria.map((item) => String(item ?? "").trim()).filter(Boolean)
    : [];
  // 中文注释：Order proof API 要求引用验收标准，Project agent 从公开 item 读模型继承，保证真实验收链可回放。
  return inherited.length > 0 ? inherited : ["proof 链接可以读取"];
}

async function runWorkbenchAction(params, client, actionId, input = {}) {
  const projectNo = params.projectNo || "";
  const roleCode = params.roleCode || "";
  const item = await findWorkbenchItem(client, { projectNo, actionId, roleCode });
  const turn = await apiJson(client.session, client.baseUrl, "POST", "/api/v1/agent/turn", {
    intent: "act",
    turnId: randomUUID(),
    scene: "workbench",
    subject: { type: "workbench_item", id: item.id },
    actionId,
    input: { itemId: item.id, ...input },
  });
  const readback = await readWorkbenchItemIfAvailable(client, item.id);
  return { projectNo, item: summarizeItem(item), turn, readback };
}

async function bindChannel(params, client) {
  const projectNo = requiredProjectNo(params);
  const bindings = normalizeBindings(params.bindings);
  const results = [];
  // 中文注释：通道绑定限定在 L1/L2，先保持最轻量外部协作入口。
  for (const binding of bindings) {
    results.push(await apiJson(client.session, client.baseUrl, "POST",
      `/api/v1/projects/${encodeURIComponent(projectNo)}/channel-bindings`, binding));
  }
  const readback = await apiJson(client.session, client.baseUrl, "GET",
    `/api/v1/projects/${encodeURIComponent(projectNo)}/channel-bindings`);
  return { projectNo, results, readback };
}

async function archiveDiscussion(params, client) {
  const projectNo = requiredProjectNo(params);
  const externalRef = params.externalRef || params.proofUrl || "monopolyfun-project-inbox";
  const body = {
    provider: inferProvider(externalRef),
    externalRef,
    eventType: params.eventType || "discussion_archive",
    title: params.title || "外部讨论归档",
    body: params.body || params.summary || "Project discussion archived by MonopolyFun agent.",
    externalUrl: params.externalUrl || (externalRef.startsWith("http") ? externalRef : null),
    payload: params.payload || {},
  };
  const event = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectNo)}/channel-events`, body);
  const sourceContract = await apiJson(client.session, client.baseUrl, "GET",
    `/api/v1/projects/${encodeURIComponent(projectNo)}/memory/source-contract`);
  return { projectNo, event, sourceContract };
}

async function createValidationLaunch(params, client) {
  const projectNo = requiredProjectNo(params);
  const title = params.title || "OpenClaw 验证轮次";
  const launch = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/launches`, {
    title,
    hypothesis: params.hypothesis || params.reason || title,
    proofRequests: Array.isArray(params.proofRequests) ? params.proofRequests : [{
      title: "核心交付证据",
      intent: "提交可验证的链接、截图或结论。",
      evidenceRequirements: [{ kind: "link", description: "可读取的 proof 链接" }],
      acceptanceSignals: [{ kind: "readback", description: "验收方能确认结果" }],
      riskLevel: "medium",
    }],
    sourceRefs: Array.isArray(params.sourceRefs) ? params.sourceRefs : [],
    metadata: { agentRuntime: "openclaw", ...(params.metadata || {}) },
  });
  return { projectNo, launch };
}

async function publishValidationLaunch(params, client) {
  const projectNo = requiredProjectNo(params);
  const launchId = params.launchId || await firstLaunchId(client, projectNo, ["draft"]);
  if (!launchId) {
    throw new Error("draft validation launch not found");
  }
  const launch = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/launches/${encodeURIComponent(launchId)}/publish`);
  return { projectNo, launch };
}

async function createValidationTask(params, client) {
  const projectNo = requiredProjectNo(params);
  const launchId = params.launchId || await firstLaunchId(client, projectNo, ["live", "draft"]) || (await createValidationLaunch({
    projectNo,
    title: "OpenClaw 验证轮次",
    hypothesis: params.intent || params.reason || "补充验证任务",
  }, client)).launch.id;
  // 中文注释：反馈转任务和手动新建任务都落到当前验证轮次，避免 agent 在多轮次上下文里写散。
  const task = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/launches/${encodeURIComponent(launchId)}/tasks`, {
    title: params.title || "OpenClaw 验证任务",
    intent: params.intent || params.reason || "补充验证任务",
    deliverable: params.deliverable || "提交可验证结果、链接和结论。",
    acceptanceCriteria: Array.isArray(params.acceptanceCriteria) && params.acceptanceCriteria.length > 0
      ? params.acceptanceCriteria
      : ["结果可以读取", "结论可以复核"],
    suggestedEvidence: Array.isArray(params.suggestedEvidence) ? params.suggestedEvidence : [],
    rewardPreview: params.rewardPreview || {},
    tags: Array.isArray(params.tags) ? params.tags : ["agent_created"],
    metadata: { agentRuntime: "openclaw", ...(params.metadata || {}) },
  });
  return { projectNo, launchId, task };
}

async function claimValidationTask(params, client) {
  const projectNo = requiredProjectNo(params);
  const task = await firstValidationTask(client, projectNo, ["open", "todo"]);
  if (!task?.id) {
    throw new Error("open validation task not found");
  }
  // 中文注释：验证任务领取使用协议专属 task claim，避免与普通 Project item claim 混用。
  const claimed = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/tasks/${encodeURIComponent(task.id)}/claim`);
  return { projectNo, launchId: task.launchId, task: claimed };
}

async function submitValidationProof(params, client) {
  const projectNo = requiredProjectNo(params);
  const task = params.taskId
    ? { id: params.taskId, launchId: params.launchId || "" }
    : await firstValidationTask(client, projectNo, ["claimed", "in_progress", "open", "todo"]);
  if (!task?.id) {
    throw new Error("validation task not found");
  }
  const proofUrl = params.proofUrl || params.url || "";
  const proof = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/tasks/${encodeURIComponent(task.id)}/proof`, {
    summary: params.summary || `验证 proof 已提交：${proofUrl}`,
    evidenceItems: proofUrl ? [{ kind: "link", href: proofUrl, label: "proof" }] : [],
    linkedProofRequestIds: Array.isArray(params.linkedProofRequestIds) ? params.linkedProofRequestIds : [],
    notes: params.notes || "",
    metadata: { agentRuntime: "openclaw", ...(params.metadata || {}) },
  });
  return { projectNo, launchId: proof.launchId || task.launchId, taskId: task.id, proof };
}

async function reviewValidationProof(params, client) {
  const projectNo = requiredProjectNo(params);
  const proof = params.proofId ? { id: params.proofId } : await firstReviewQueueProof(client, projectNo);
  if (!proof?.id) {
    throw new Error("submitted validation proof not found");
  }
  const reviewed = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/proofs/${encodeURIComponent(proof.id)}/review`, {
    result: normalizeValidationReviewDecision(params.decision || params.result),
    reason: params.reason || "验证证据已复核。",
    validationMode: params.validationMode || "ordinary",
    requestedEvidence: Array.isArray(params.requestedEvidence) ? params.requestedEvidence : [],
    riskFlags: Array.isArray(params.riskFlags) ? params.riskFlags : [],
    scoreInputs: params.scoreInputs || {},
    metadata: { agentRuntime: "openclaw", ...(params.metadata || {}) },
  });
  return { projectNo, launchId: reviewed.launchId, proof: reviewed };
}

async function createValidationFeedback(params, client) {
  const projectNo = requiredProjectNo(params);
  const feedback = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/validation-feedback`, {
    launchId: params.launchId || undefined,
    subjectType: params.subjectType || "project",
    subjectId: params.subjectId || projectNo,
    intent: params.intent || params.reason || "Project feedback",
    reason: params.reason || params.intent || "Project feedback",
    evidence: Array.isArray(params.evidence) ? params.evidence : [],
    suggestedAction: params.suggestedAction || "review",
    metadata: { agentRuntime: "openclaw", ...(params.metadata || {}) },
  });
  return { projectNo, feedback };
}

async function resolveValidationFeedback(params, client) {
  const projectNo = requiredProjectNo(params);
  const feedbackId = params.feedbackId || await firstOpenFeedbackId(client, projectNo);
  if (!feedbackId) {
    throw new Error("feedback item not found");
  }
  const feedback = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/validation-feedback/${encodeURIComponent(feedbackId)}/resolve`, {
    status: params.status || "resolved",
    resolution: params.resolution || params.reason || "反馈已处理。",
    metadata: { agentRuntime: "openclaw", ...(params.metadata || {}) },
  });
  return { projectNo, feedback };
}

async function settleValidationLaunch(params, client) {
  const projectNo = requiredProjectNo(params);
  let launchId = params.launchId || await firstLaunchId(client, projectNo, ["live", "reviewing", "draft"]);
  if (!launchId) {
    throw new Error("validation launch not found");
  }
  const launches = await listValidationLaunches(client, projectNo);
  const currentLaunch = launches.find((item) => item.id === launchId);
  if (currentLaunch?.status === "draft") {
    const published = await publishValidationLaunch({ projectNo, launchId }, client);
    launchId = published.launch.id;
  }
  const settledLaunch = await apiJson(client.session, client.baseUrl, "POST", `/api/v1/projects/${encodeURIComponent(projectNo)}/launches/${encodeURIComponent(launchId)}/settle`, {
    reason: params.reason || "OpenClaw 验证奖励结算。",
    scoreSnapshot: params.scoreSnapshot || {},
    curveSnapshot: params.curveSnapshot || {},
    rewardSnapshot: params.rewardSnapshot || {},
    metadata: { agentRuntime: "openclaw", ...(params.metadata || {}) },
  });
  return { projectNo, launch: settledLaunch };
}

async function listValidationLaunches(client, projectNo) {
  const launches = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/launches`);
  return Array.isArray(launches) ? launches : [];
}

async function firstLaunchId(client, projectNo, preferredStatuses = ["live", "reviewing", "draft"]) {
  const items = await listValidationLaunches(client, projectNo);
  return items.find((item) => preferredStatuses.includes(String(item.status ?? "")))?.id ?? items[0]?.id ?? "";
}

async function firstValidationTask(client, projectNo, preferredStatuses) {
  const launches = await listValidationLaunches(client, projectNo);
  for (const launch of launches) {
    const tasks = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/launches/${encodeURIComponent(launch.id)}/tasks`);
    const items = Array.isArray(tasks) ? tasks : [];
    const match = items.find((item) => preferredStatuses.includes(String(item.status ?? ""))) ?? items[0];
    if (match) {
      return match;
    }
  }
  return null;
}

async function firstReviewQueueProof(client, projectNo) {
  const queue = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/review-queue`);
  const items = Array.isArray(queue) ? queue : [];
  return items[0]?.proof ?? null;
}

async function firstOpenFeedbackId(client, projectNo) {
  const feedback = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/validation-feedback`);
  const items = Array.isArray(feedback) ? feedback : [];
  return items.find((item) => ["open", "changes_requested", "held"].includes(String(item.status ?? "")))?.id ?? items[0]?.id ?? "";
}

async function createAppeal(params, client) {
  const projectNo = requiredProjectNo(params);
  const body = {
    subjectType: params.subjectType || "work_receipt",
    subjectId: params.subjectId || params.itemId || "current",
    reason: required(params.reason, "reason"),
    requestedAction: params.requestedAction || "review_again",
    metadata: params.metadata || { agentRuntime: "monopolyfun-agent" },
  };
  const appeal = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectNo)}/appeals`, body);
  const appeals = await apiJson(client.session, client.baseUrl, "GET",
    `/api/v1/projects/${encodeURIComponent(projectNo)}/appeals`);
  return { projectNo, appeal, appeals };
}

async function resolveAppeal(params, client) {
  const projectNo = requiredProjectNo(params);
  const appealId = params.appealId || await findOpenAppeal(projectNo, client);
  const body = {
    status: params.status || "resolved",
    resolution: params.resolution || "申诉已处理。",
    metadata: params.metadata || { agentRuntime: "monopolyfun-agent" },
  };
  const appeal = await apiJson(client.session, client.baseUrl, "POST",
    `/api/v1/projects/${encodeURIComponent(projectNo)}/appeals/${encodeURIComponent(appealId)}/resolve`, body);
  return { projectNo, appealId, appeal };
}

function submitReceiptInput(params, client) {
  const proofUrl = params.proofUrl || params.url || "";
  return {
    actorAccountId: client.account.id,
    summary: params.summary || `proof 已提交：${proofUrl}`,
    output: { proofUrl },
    sourceReceipt: { source: "monopolyfun-agent", proofUrl },
    evidenceRefs: proofUrl ? [proofUrl] : [],
    artifacts: proofUrl ? [proofUrl] : [],
    agentRuntime: "openclaw",
  };
}

function isRevisionDecision(decision) {
  return ["request_changes", "revision_requested", "revise", "changes_requested", "hold", "held"].includes(String(decision || "").trim().toLowerCase());
}

function normalizeReviewDecision(decision) {
  const normalized = String(decision || "").trim().toLowerCase();
  if (["accept", "accepted", "approve", "approved"].includes(normalized)) {
    return "accepted";
  }
  return "accepted";
}

function normalizeValidationReviewDecision(decision) {
  const normalized = String(decision || "").trim().toLowerCase();
  if (["request_changes", "changes_requested", "revise", "revision_requested"].includes(normalized)) {
    return "request_changes";
  }
  if (["hold", "held", "risk"].includes(normalized)) {
    return "hold";
  }
  return "accept";
}

async function findWorkbenchItem(client, filter) {
  const items = normalizeItems(await apiJson(client.session, client.baseUrl, "GET", "/api/v1/workbench"));
  const matches = items.filter((item) => {
    if (filter.actionId && !itemHasAction(item, filter.actionId)) {
      return false;
    }
    if (filter.projectNo && !itemMatchesText(item, filter.projectNo)) {
      return false;
    }
    if (filter.roleCode && !itemMatchesText(item, filter.roleCode)) {
      return false;
    }
    return true;
  });
  if (matches.length === 0) {
    throw new Error(`workbench item not found for action ${filter.actionId}`);
  }
  return matches[0];
}

async function readWorkbenchItemIfAvailable(client, itemId) {
  try {
    return await apiJson(client.session, client.baseUrl, "GET", `/api/v1/work/items/${encodeURIComponent(itemId)}`);
  } catch {
    return null;
  }
}

async function resolveAccountId(account, client) {
  const value = required(account, "account");
  if (value.startsWith("acct-") || value.startsWith("account-")) {
    return value;
  }
  const lookup = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/accounts/lookup?ids=${encodeURIComponent(value.replace(/^@+/, ""))}`);
  const first = Array.isArray(lookup) ? lookup[0] : null;
  if (!first?.id) {
    throw new Error(`account not found: ${value}`);
  }
  return first.id;
}

async function currentAccount(session, baseUrl) {
  const me = await apiJson(session, baseUrl, "GET", "/api/v1/auth/me");
  const account = me?.account ?? {};
  if (!account.id) {
    throw new Error("current account missing");
  }
  return {
    id: String(account.id),
    handle: String(account.handle ?? ""),
    displayName: String(account.displayName ?? account.handle ?? ""),
  };
}

async function findOpenAppeal(projectNo, client) {
  const appeals = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/appeals`);
  const open = Array.isArray(appeals) ? appeals.find((appeal) => appeal.status === "open") : null;
  if (!open?.id) {
    throw new Error("open appeal not found");
  }
  return open.id;
}

function normalizeItems(input) {
  const source = Array.isArray(input) ? input : Array.isArray(input?.items) ? input.items : [];
  return source.map((item) => ({
    ...item,
    id: String(item?.id ?? item?.itemId ?? ""),
    title: String(item?.title ?? ""),
    reason: String(item?.reason ?? ""),
    target: item?.target ?? {},
    requiredRoleCode: item?.requiredRoleCode ?? "",
    actions: Array.isArray(item?.actions)
      ? item.actions.map((action) => typeof action === "string" ? { id: action } : { ...action, id: String(action?.id ?? "") })
      : [],
    nextTurn: item?.nextTurn ?? null,
  }));
}

function itemHasAction(item, actionId) {
  return item.actions.some((action) => action.id === actionId);
}

function itemMatchesText(item, value) {
  return JSON.stringify(item).toLowerCase().includes(String(value).toLowerCase());
}

function summarizeItem(item) {
  return {
    id: item.id,
    title: item.title,
    reason: item.reason,
    target: item.target,
    requiredRoleCode: item.requiredRoleCode,
    actions: item.actions.map((action) => action.id),
  };
}

function summarizeProjectItem(item) {
  return {
    id: item.id,
    title: item.title,
    status: item.status,
    activeOrderNo: item.activeOrderNo,
  };
}

function summarizeLedger(ledger) {
  const tasks = Array.isArray(ledger?.tasks) ? ledger.tasks : [];
  return {
    status: ledger?.status,
    taskCount: tasks.length,
    tasks: tasks.map((task) => ({
      id: task.id ?? task.taskId,
      title: task.title ?? task.name,
      status: task.status,
    })),
  };
}

function summarizeProjectDashboard(dashboard) {
  const items = Array.isArray(dashboard?.workspace?.items) ? dashboard.workspace.items : [];
  return {
    itemCount: items.length,
    items: items.slice(0, 5).map((item) => ({
      id: item.id,
      title: item.title,
      status: item.status,
      activeOrderNo: item.activeOrderNo,
    })),
  };
}

function normalizeBindings(bindings) {
  if (Array.isArray(bindings) && bindings.length > 0) {
    return bindings
      .filter((binding) => ["L1", "L2"].includes(binding.level))
      .map((binding) => ({
        level: binding.level,
        provider: binding.provider,
        channelType: binding.channelType,
        externalRef: binding.externalRef,
        displayName: binding.displayName || `${binding.provider}:${binding.externalRef}`,
        actionPolicy: binding.actionPolicy || { actionEntry: "project_inbox" },
        archivePolicy: binding.archivePolicy || { mode: "manual" },
      }));
  }
  return [{
    level: "L1",
    provider: "github",
    channelType: "issue",
    externalRef: "monopolyfun/project#manual",
    displayName: "github:monopolyfun/project#manual",
    actionPolicy: { actionEntry: "project_inbox" },
    archivePolicy: { mode: "manual" },
  }];
}

function inferProvider(externalRef) {
  if (externalRef.includes("@")) {
    return "email";
  }
  if (externalRef.includes("github") || externalRef.includes("#")) {
    return "github";
  }
  return "monopolyfun";
}

async function resolveProjectId(params, client) {
  const direct = String(params.projectId || "").trim();
  if (direct) {
    return direct;
  }
  const projectNo = await resolveProjectNo(params, client);
  const project = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}`);
  const projectId = project?.id ?? project?.project?.id ?? await resolveProjectIdFromDashboard(projectNo, client);
  if (!projectId) {
    throw new Error(`project id not found for ${projectNo}`);
  }
  return String(projectId);
}

async function resolveProjectIdFromDashboard(projectNo, client) {
  const dashboard = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectNo)}/dashboard`);
  const sourceRef = String(dashboard?.workspace?.market?.sourceRef ?? "");
  const sourceMatch = sourceRef.match(/^project:\/\/(.+)$/);
  if (sourceMatch?.[1]) {
    return sourceMatch[1];
  }
  const item = Array.isArray(dashboard?.workspace?.items) ? dashboard.workspace.items[0] : null;
  return item?.postId ?? "";
}

async function resolveWorkThreadLocator(params, client, preferredStatuses = []) {
  const hasProjectLocator = Boolean(
    params.projectNo
    || params.projectId
    || params.projectQuery
    || params.companyName
    || params.projectName
    || JSON.stringify(params).match(PROJECT_NO_PATTERN),
  );
  if (!hasProjectLocator) {
    const discovered = await firstVisibleWorkThread(client, preferredStatuses);
    if (discovered) {
      return discovered;
    }
  }
  const projectNo = await resolveProjectNo(params, client);
  const projectId = await resolveProjectId({ ...params, projectNo }, client);
  const workThreadId = params.workThreadId || params.threadId || await firstWorkThreadId(client, projectId, preferredStatuses);
  if (!workThreadId) {
    throw new Error(`work thread not found for ${projectNo}`);
  }
  return { projectNo, projectId, workThreadId };
}

async function firstVisibleWorkThread(client, preferredStatuses = []) {
  // 中文注释：dev 的真实入口通常只有“今天有什么可以做”，这里从公开项目读模型发现可领取 WorkThread。
  const page = await apiJson(client.session, client.baseUrl, "GET", "/api/v1/projects?sort=recent&limit=20&includeAgent=true");
  const projects = Array.isArray(page?.items) ? page.items : Array.isArray(page) ? page : [];
  for (const project of projects) {
    const projectNo = String(project?.projectNo ?? project?.project?.projectNo ?? project?.id ?? "");
    if (!projectNo) {
      continue;
    }
    try {
      const projectId = await resolveProjectId({ projectNo }, client);
      const workThreadId = await firstWorkThreadId(client, projectId, preferredStatuses);
      if (workThreadId) {
        return { projectNo, projectId, workThreadId };
      }
    } catch {
      // 中文注释：公开列表可能包含当前账号无权读取的项目，跳过后继续找可执行任务。
    }
  }
  return null;
}

async function firstWorkThreadId(client, projectId, preferredStatuses = []) {
  const threads = await apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectId)}/work-threads`);
  const items = Array.isArray(threads) ? threads : [];
  const preferred = items.find((item) => preferredStatuses.includes(String(item.status ?? "")));
  return preferred?.id ?? items[0]?.id ?? "";
}

async function readWorkroom(client, projectId) {
  return apiJson(client.session, client.baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(projectId)}/workroom`);
}

async function latestDistributionPeriod(client, projectId) {
  try {
    const workroom = await readWorkroom(client, projectId);
    const distributions = Array.isArray(workroom?.distributions) ? workroom.distributions : [];
    return distributions[0]?.period ?? "";
  } catch {
    return "";
  }
}

function normalizeList(value, fallback) {
  if (Array.isArray(value)) {
    const items = value.map((item) => String(item ?? "").trim()).filter(Boolean);
    return items.length > 0 ? items : fallback;
  }
  if (typeof value === "string" && value.trim()) {
    return value.split(/[、,，;\n]/).map((item) => item.trim()).filter(Boolean);
  }
  return fallback;
}

function buildWorkThreadResultMarkdown(workThreadId, params) {
  const evidence = normalizeList(params.evidenceRefs, params.prUrl ? [params.prUrl] : []);
  const changedFiles = normalizeList(params.changedFiles, []);
  return [
    "---",
    "packetType: work_result",
    `workThreadId: ${workThreadId}`,
    "---",
    "# Result",
    "",
    "## Summary",
    params.summary || "OpenClaw 已完成 WorkThread 交付。",
    "",
    "## Evidence",
    ...(evidence.length > 0 ? evidence.map((item) => `- ${item}`) : ["- 已提交结构化结果"]),
    "",
    "## Tests",
    params.testSummary || "测试摘要已随结果提交。",
    "",
    "## Changed Files",
    ...(changedFiles.length > 0 ? changedFiles.map((item) => `- ${item}`) : ["- 未提供文件列表"]),
    "",
  ].join("\n");
}

function normalizeWorkThreadDecision(decision) {
  const normalized = String(decision || "").trim().toLowerCase();
  if (["reject", "rejected", "request_changes", "changes_requested"].includes(normalized)) {
    return "reject";
  }
  return "accept";
}

function summarizeWorkThreadPacket(packet) {
  return {
    projectNo: packet?.projectNo,
    projectId: packet?.projectId,
    workThreadId: packet?.workThreadId,
    threadNo: packet?.threadNo,
    taskValue: packet?.taskValue,
    bountyAmountMinor: packet?.bountyAmountMinor,
    bountyToken: packet?.bountyToken,
    repoRef: packet?.repoRef,
    issueUrl: packet?.issueUrl,
  };
}

function summarizeWorkroom(workroom) {
  const workThreads = Array.isArray(workroom?.workThreads) ? workroom.workThreads : [];
  const distributions = Array.isArray(workroom?.distributions) ? workroom.distributions : [];
  return {
    projectNo: workroom?.projectNo ?? "",
    projectId: workroom?.projectId ?? "",
    owner: Boolean(workroom?.owner),
    workThreadCount: workThreads.length,
    workThreads: workThreads.slice(0, 5).map((item) => ({
      id: item.id,
      title: item.title,
      status: item.status,
      assigneeAccountId: item.assigneeAccountId,
      latestResultStatus: item.latestResult?.status,
    })),
    claimableAmountMinor: workroom?.myRewards?.claimableAmountMinor ?? 0,
    distributions: distributions.slice(0, 5).map((item) => ({
      period: item.period,
      totalRevenueMinor: item.totalRevenueMinor,
      myClaimableAmountMinor: item.myClaimableAmountMinor,
      status: item.status,
    })),
  };
}

function currentPeriod() {
  return new Date().toISOString().slice(0, 7);
}

async function resolveProjectNo(params, client) {
  const direct = params.projectNo || JSON.stringify(params).match(PROJECT_NO_PATTERN)?.[0];
  if (direct) {
    return String(direct).trim();
  }
  const query = String(params.projectQuery || params.companyName || params.projectName || "").trim();
  if (!query) {
    return requiredProjectNo(params);
  }
  const page = await apiJson(client.session, client.baseUrl, "GET",
    `/api/v1/projects?q=${encodeURIComponent(query)}&sort=recent&limit=5&includeAgent=true`);
  const items = Array.isArray(page?.items) ? page.items : Array.isArray(page) ? page : [];
  const exact = items.find((item) => projectMatchesQuery(item, query)) ?? items[0];
  const projectNo = exact?.projectNo ?? exact?.project?.projectNo ?? exact?.id;
  if (!projectNo) {
    throw new Error(`project not found for query: ${query}`);
  }
  return String(projectNo);
}

function projectMatchesQuery(project, query) {
  const normalized = String(query || "").toLowerCase();
  const fields = [
    project?.projectNo,
    project?.title,
    project?.name,
    project?.summary,
    project?.oneSentence,
    project?.metadata?.description,
    project?.metadata?.goal,
  ];
  return fields.some((field) => String(field || "").toLowerCase().includes(normalized));
}

function requiredProjectNo(params) {
  const value = params.projectNo || JSON.stringify(params).match(PROJECT_NO_PATTERN)?.[0];
  return required(value, "projectNo");
}

function required(value, name) {
  if (value === undefined || value === null || String(value).trim() === "") {
    throw new Error(`${name} is required`);
  }
  return String(value).trim();
}

function mergeParams(state, params) {
  // 中文注释：路由脚本会输出空字符串占位，执行阶段保留 state 中的项目、钱包和周期上下文。
  const cleanParams = Object.fromEntries(Object.entries(params).filter(([, value]) => {
    return !(typeof value === "string" && value.trim() === "");
  }));
  const hasProjectLocator = Boolean(
    cleanParams.projectNo
    || cleanParams.projectQuery
    || cleanParams.companyName
    || cleanParams.projectName
    || JSON.stringify(cleanParams).match(PROJECT_NO_PATTERN),
  );
  // 中文注释：用户给出公司名/项目名时优先实时检索，避免旧会话 state 把请求导向上一个项目。
  return {
    ...(hasProjectLocator ? {} : { projectNo: state.projectNo }),
    appealId: state.appealId,
    ...(hasProjectLocator ? {} : {
      projectId: state.projectId,
      workThreadId: state.workThreadId,
      period: state.period,
      walletAddress: state.walletAddress,
    }),
    ...cleanParams,
  };
}

function nextStateFor(action, state, result) {
  const next = { ...state, updatedAt: new Date().toISOString() };
  const projectNo = result?.projectNo ?? result?.result?.projectNo;
  if (projectNo) {
    next.projectNo = projectNo;
  }
  const projectId = result?.projectId ?? result?.workroom?.projectId;
  if (projectId) {
    next.projectId = projectId;
  }
  const workThreadId = result?.workThreadId ?? result?.workThread?.id ?? result?.result?.workThreadId;
  if (workThreadId) {
    next.workThreadId = workThreadId;
  }
  const period = result?.period ?? result?.distribution?.period ?? result?.claim?.period;
  if (period) {
    next.period = period;
  }
  const walletAddress = result?.walletAddress ?? result?.claim?.walletAddress;
  if (walletAddress) {
    next.walletAddress = walletAddress;
  }
  const appealId = result?.appeal?.id ?? result?.appealId;
  if (action === "create-appeal" && appealId) {
    next.appealId = appealId;
  }
  const launchId = result?.launch?.id ?? result?.launchId;
  if (launchId) {
    next.launchId = launchId;
  }
  const deliverySessionId = result?.repoDelivery?.deliverySessionId;
  if (deliverySessionId) {
    next.repoDeliverySessionId = deliverySessionId;
  }
  if (result?.phase) {
    next.deliveryPhase = result.phase;
  }
  const feedbackId = result?.feedback?.id ?? result?.feedbackId;
  if (feedbackId) {
    next.feedbackId = feedbackId;
  }
  return next;
}

async function readState(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch {
    return {};
  }
}

async function writeState(filePath, state) {
  await mkdir(dirname(filePath), { recursive: true });
  await writeFile(filePath, `${JSON.stringify(state, null, 2)}\n`, { mode: 0o600 });
}

function parseJsonOption(value, name) {
  try {
    return JSON.parse(value);
  } catch (error) {
    throw new Error(`${name} must be valid JSON: ${error.message}`);
  }
}

function parseJson(value) {
  try {
    return JSON.parse(String(value ?? ""));
  } catch {
    return null;
  }
}
