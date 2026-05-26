#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { homedir } from "node:os";
import { buildFailurePayload, parseArgs, printJson } from "./runtime-session.mjs";
import { actionDocPath, actionKeysByFlag, actionReply } from "./project-action-registry.mjs";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const WRITE_ACTIONS = actionKeysByFlag("write");
const WORKBENCH_ACTIONS = actionKeysByFlag("workbench");
const DEFAULT_STATE_FILE = join(homedir(), ".openclaw", "monopolyfun", "action-state.json");

const { args, flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write([
    "usage: node scripts/agent-turn.mjs --text '<raw user message>'",
    "options:",
    "  --text raw user message",
    "",
  ].join("\n") + "\n");
  process.exit(0);
}

try {
  const text = String(flags.get("text") ?? args.join(" ")).trim();
  if (!text) {
    throw new Error("text is required");
  }
  const result = await runAgentTurn(text);
  printJson(result);
} catch (error) {
  printJson(buildFailurePayload(error, { phase: "agent_turn" }));
  process.exit(1);
}

async function runAgentTurn(text) {
  const guidedProject = await runGuidedProjectCreation(text);
  if (guidedProject) {
    return guidedProject;
  }

  if (isWorkbenchNotice(text)) {
    const projectNo = extractProjectNo(text);
    const workbench = runJson("workbench-current.mjs", ["--project-no", projectNo]);
    return okPayload({
      text,
      actionKey: "workbench-current",
      projectNo: projectNo || workbench.projectNo || "",
      workbench: summarizeWorkbench(workbench),
      userVisibleText: formatWorkbenchReply(projectNo, workbench),
    });
  }

  const route = runJson("route-intent.mjs", ["--text", text]);
  if (route.status !== "ok" || !route.actionKey || route.actionKey === "unknown") {
    return {
      status: "needs_user_input",
      text,
      route,
      userVisibleText: "我还不能确定要执行哪个 MonopolyFun 项目动作，请补充项目编号或具体动作。",
    };
  }

  const originalActionKey = String(route.actionKey);
  const recovery = recoverableAction(route, text);
  const actionKey = recovery?.actionKey ?? originalActionKey;
  const actionDoc = actionDocPath(actionKey) ? await readActionDoc(actionDocPath(actionKey)) : "";
  const params = route.params && typeof route.params === "object" ? route.params : {};
  const workbench = WORKBENCH_ACTIONS.has(actionKey)
    ? runOptionalJson("workbench-current.mjs", ["--project-no", String(params.projectNo ?? "")])
    : null;
  const executed = runJson("execute-action.mjs", [
    "--action",
    actionKey,
    "--params",
    JSON.stringify(recovery?.params ?? params),
  ]);
  const projectNo = resolveProjectNo(params, executed);
  const verified = WRITE_ACTIONS.has(actionKey)
    ? runOptionalJson("verify-action.mjs", ["--action", actionKey, "--project-no", projectNo])
    : null;

  return okPayload({
    text,
    actionKey,
    projectNo,
    route: compactRoute(route),
    recovery,
    actionDoc: summarizeActionDoc(actionDoc),
    workbench: summarizeWorkbench(workbench),
    executed: summarizeExecution(executed),
    verified: summarizeVerification(verified),
    userVisibleText: formatActionReply(actionKey, executed, verified, recovery),
  });
}

async function runGuidedProjectCreation(text) {
  const stateFile = process.env.MONOPOLYFUN_ACTION_STATE_FILE || DEFAULT_STATE_FILE;
  const state = await readJsonFile(stateFile);
  const draft = state.guidedProject;
  const value = String(text ?? "").trim();
  const lower = value.toLowerCase();
  if (!draft && isVagueProjectCreateRequest(lower)) {
    await writeJsonFile(stateFile, {
      ...state,
      guidedProject: {
        step: "company",
        startedAt: new Date().toISOString(),
        originalText: value,
      },
    });
    // 中文注释：低信息创建请求先收集关键槽位，避免 OpenClaw 把真实用户一句话直接写成空项目。
    return {
      status: "needs_user_input",
      text,
      actionKey: "create-project",
      guided: { step: "company" },
      userVisibleText: "可以。公司或项目名叫什么？",
    };
  }
  if (!draft || draft.kind === "completed") {
    return null;
  }
  if (draft.step === "company") {
    const companyName = cleanupGuidedAnswer(value);
    await writeJsonFile(stateFile, {
      ...state,
      guidedProject: {
        ...draft,
        step: "goal",
        companyName,
        title: companyName,
        updatedAt: new Date().toISOString(),
      },
    });
    return {
      status: "needs_user_input",
      text,
      actionKey: "create-project",
      guided: { step: "goal", companyName },
      userVisibleText: "目标是什么？说一句你希望这个项目完成的结果就行。",
    };
  }
  if (draft.step === "goal") {
    const goal = cleanupGuidedAnswer(value);
    await writeJsonFile(stateFile, {
      ...state,
      guidedProject: {
        ...draft,
        step: "first_task",
        goal,
        updatedAt: new Date().toISOString(),
      },
    });
    return {
      status: "needs_user_input",
      text,
      actionKey: "create-project",
      guided: { step: "first_task", companyName: draft.companyName, goal },
      userVisibleText: "第一件要交给别人完成的任务是什么？",
    };
  }
  if (draft.step === "first_task") {
    const firstTask = cleanupGuidedAnswer(value);
    const params = {
      title: draft.title || draft.companyName || `Project ${new Date().toISOString().slice(0, 10)}`,
      description: `${draft.companyName || "项目"}：${draft.goal || firstTask}`,
      goal: draft.goal || firstTask,
      taskName: firstTask,
      provisionSessionId: await readOptionalText(join(homedir(), ".openclaw", "monopolyfun", "provision-session-id.txt")),
      items: [{
        name: firstTask,
        description: firstTask,
        deliveryStandard: "提交可复核的结果、证据和结论。",
        acceptanceCriteria: ["结果可以复核", "证据可以读回", "负责人可以验收"],
        difficultyScore: 5,
      }],
    };
    const executed = runJson("execute-action.mjs", [
      "--action",
      "create-project",
      "--params",
      JSON.stringify(params),
    ]);
    const projectNo = resolveProjectNo(params, executed);
    const verified = runOptionalJson("verify-action.mjs", ["--action", "create-project", "--project-no", projectNo]);
    const nextState = await readJsonFile(stateFile);
    await writeJsonFile(stateFile, {
      ...nextState,
      guidedProject: {
        kind: "completed",
        companyName: draft.companyName,
        goal: draft.goal,
        firstTask,
        completedAt: new Date().toISOString(),
      },
    });
    return okPayload({
      text,
      actionKey: "create-project",
      projectNo,
      route: {
        actionKey: "create-project",
        confidence: 0.92,
        projectNo,
      },
      guided: {
        companyName: draft.companyName,
        goal: draft.goal,
        firstTask,
      },
      actionDoc: summarizeActionDoc(await readActionDoc(actionDocPath("create-project"))),
      executed: summarizeExecution(executed),
      verified: summarizeVerification(verified),
      userVisibleText: `项目已创建：${projectNo}。我已经把第一件任务放进项目，下一步可以邀请 dev 或直接发布给人领取。`,
    });
  }
  return null;
}

function isVagueProjectCreateRequest(value) {
  const wantsCreate = /创建|新建|开个|开一个|我想开|create/.test(value);
  const projectWord = /项目|project/.test(value);
  const hasSpecificSlot = /项目名|项目名称|名字叫|目标|goal|任务|收益|测试链|txhash|workthread|分账/i.test(value);
  return wantsCreate && projectWord && !hasSpecificSlot;
}

function cleanupGuidedAnswer(value) {
  return String(value ?? "")
    .replace(/^(公司名|项目名|名字|目标|任务)\s*(是|叫|为|:|：)?\s*/i, "")
    .trim();
}

async function readJsonFile(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch {
    return {};
  }
}

async function readOptionalText(filePath) {
  try {
    return (await readFile(filePath, "utf8")).trim();
  } catch {
    return "";
  }
}

async function writeJsonFile(filePath, value) {
  await mkdir(dirname(filePath), { recursive: true });
  await writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}

function recoverableAction(route, text) {
  const params = route.params && typeof route.params === "object" ? route.params : {};
  // 中文注释：自然话术“结果可以过”默认按普通交付验收兜底，避免误入 validation proof 队列后停止协作。
  if (route.actionKey === "review-validation-proof" && isNaturalDeliveryAcceptance(text) && !hasExplicitValidationScope(text)) {
    return {
      fromActionKey: route.actionKey,
      actionKey: "review-proof",
      reason: "natural_delivery_acceptance",
      params: {
        ...params,
        decision: "accepted",
        reason: params.reason || text,
      },
    };
  }
  return null;
}

function runJson(scriptName, scriptArgs) {
  const result = spawnSync(process.execPath, [join(scriptDir, scriptName), ...scriptArgs], {
    cwd: scriptDir,
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 20,
    env: process.env,
  });
  const parsed = parseJson(result.stdout);
  if (result.status !== 0) {
    const detail = parsed ? JSON.stringify(parsed) : result.stderr || result.stdout;
    throw new Error(`${scriptName} failed: ${detail}`);
  }
  if (!parsed) {
    throw new Error(`${scriptName} returned invalid JSON`);
  }
  return parsed;
}

function runOptionalJson(scriptName, scriptArgs) {
  try {
    return runJson(scriptName, scriptArgs);
  } catch (error) {
    // 中文注释：workbench/readback 是辅助上下文，主动作执行结果优先；失败信息仍写入 turn 结果供 trace 排查。
    return {
      status: "unavailable",
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

async function readActionDoc(actionDoc) {
  const safeName = String(actionDoc)
    .replace(/^actions\//, "")
    .replace(/\.md$/i, "")
    .replace(/[^a-z0-9-]/gi, "");
  if (!safeName) {
    return "";
  }
  return readFile(join(scriptDir, "..", "actions", `${safeName.replace(/\.md$/i, "")}.md`), "utf8");
}

function okPayload(payload) {
  return {
    status: "ok",
    ...payload,
  };
}

function compactRoute(route) {
  return {
    actionKey: route.actionKey,
    confidence: route.confidence,
    projectNo: route.params?.projectNo ?? "",
  };
}

function summarizeActionDoc(text) {
  const firstLine = String(text).split("\n").find((line) => line.trim().startsWith("#"));
  return {
    loaded: Boolean(text),
    title: firstLine ? firstLine.replace(/^#+\s*/, "").trim() : "",
    chars: String(text).length,
  };
}

function formatWorkbenchReply(projectNo, workbench) {
  const items = Array.isArray(workbench?.items) ? workbench.items : [];
  if (items.length === 0) {
    return projectNo
      ? `项目 ${projectNo} 当前没有需要立即处理的新待办。`
      : "当前没有需要立即处理的新待办。";
  }
  const lines = items.map((item) => {
    const actions = Array.isArray(item.actions) && item.actions.length > 0 ? item.actions.join(" / ") : "无可执行动作";
    return `- ${item.title || item.reason || item.id}\n  - 类型：\`${item.reason || "-"}\`\n  - 可用操作：\`${actions}\``;
  });
  return [
    `项目 ${projectNo || workbench.projectNo || ""} 当前有 ${items.length} 个新待办：`.trim(),
    "",
    ...lines,
  ].join("\n");
}

function formatActionReply(actionKey, executed, verified, recovery = null) {
  if (executed?.status && executed.status !== "ok") {
    return `执行被阻塞：${executed.message ?? JSON.stringify(executed)}`;
  }
  const result = executed?.result ?? {};
  const projectNo = resolveProjectNo({}, executed);
  switch (actionKey) {
    case "create-project":
      return `项目已创建：${projectNo}，初始化任务已生成。`;
    case "invite-role":
      return `已邀请 ${result.accountId ?? ""} 以 ${result.roleCode ?? ""} 加入项目 ${projectNo}。`;
    case "accept-invite":
      return `已接受项目 ${projectNo} 的邀请。`;
    case "claim-task":
      return `已领取项目 ${projectNo} 的当前任务。`;
    case "develop-task":
      return formatDevelopReply(projectNo, result);
    case "submit-proof":
      return `项目 ${projectNo} 的 proof 已提交。`;
    case "review-proof":
      if (recovery?.fromActionKey === "review-validation-proof") {
        return [
          `项目 ${projectNo} 的交付已验收通过。`,
          "",
          "复盘：这句话命中了“结果可以过/验收通过”，本轮按普通交付验收链路处理；validation proof 复核保留给“验证证据/这轮验证”这类明确场景。",
        ].join("\n");
      }
      return `项目 ${projectNo} 的交付已验收通过。`;
    case "create-workthread":
      return `项目 ${projectNo} 的 WorkThread 已创建：${result.workThread?.id ?? result.workThreadId ?? ""}。`;
    case "claim-workthread":
      return `已领取项目 ${projectNo} 的 WorkThread：${result.workThreadId ?? ""}。`;
    case "submit-workthread-result":
      return `项目 ${projectNo} 的 WorkThread 结果已提交：${result.result?.resultNo ?? result.result?.id ?? ""}。`;
    case "review-workthread":
      return `项目 ${projectNo} 的 WorkThread 已完成验收。`;
    case "upsert-revenue-address":
      return `项目 ${projectNo} 的收益合约地址已绑定，chainId=${result.revenueAddress?.chainId ?? ""}。`;
    case "create-distribution":
      return `项目 ${projectNo} 的 ${result.period ?? ""} 收益分配已发布。`;
    case "claim-revenue":
      return formatClaimRevenueReply(projectNo, result);
    case "approve-share-release":
      return `项目 ${projectNo} 的 shares 发放审批已提交。`;
    case "bind-channel":
      return `项目 ${projectNo} 的 L1/L2 协作记录已绑定。`;
    case "archive-discussion":
      return `项目 ${projectNo} 的外部讨论已归档。`;
    case "create-appeal":
      return `项目 ${projectNo} 的申诉已创建。`;
    case "resolve-appeal":
      return `项目 ${projectNo} 的申诉已处理。`;
    default:
      return actionReply(actionKey, projectNo) || (verified?.status === "ok" ? "动作已执行并完成读回验证。" : "动作已执行。");
  }
}

function formatDevelopReply(projectNo, result) {
  const repo = result.repoDelivery ?? {};
  const worker = result.repoWorker ?? {};
  if (result.phase === "delivery_ready") {
    return [
      `我已经完成项目 ${projectNo} 的代码交付，PR 和 proof 都提交好了。`,
      repo.prUrl ? `PR：${repo.prUrl}` : "",
      "你点开链接看结果，确认可以后我会继续帮你走验收。",
    ].filter(Boolean).join("\n");
  }
  if (result.phase === "development_started") {
    const lines = [
      `我找到了项目 ${projectNo} 的当前任务，已经领取并开始开发。`,
      repo.repoUrl ? `仓库：${repo.repoUrl}` : "",
      repo.headBranch ? `分支：${repo.headBranch}` : "",
      worker.workdir ? `工作区：${worker.workdir}` : "",
      "完成后我会主动发 PR、预览或 proof 链接给你确认。",
    ];
    return lines.filter(Boolean).join("\n");
  }
  if (result.phase === "blocked_need_user") {
    return [
      `我检查了项目 ${projectNo}，当前卡在：${result.reason || "需要用户补充信息"}。`,
      result.message || "",
      "我会保留这次处理记录，等拿到缺少的信息后继续推进。",
    ].filter(Boolean).join("\n");
  }
  return `项目 ${projectNo} 的开发任务已推进。`;
}

function formatClaimRevenueReply(projectNo, result) {
  const claim = result.claim ?? {};
  if (claim.txHash) {
    if (claim.status === "claimed") {
      return `项目 ${projectNo} 的收益领取已确认：${claim.txHash}。`;
    }
    return `项目 ${projectNo} 的收益领取 txHash 已记录：${claim.txHash}，状态 ${claim.status ?? ""}。`;
  }
  return [
    `项目 ${projectNo} 的收益可领取：${claim.amountMinor ?? 0} ${claim.token ?? ""}。`,
    result.walletAddress ? `钱包：${result.walletAddress}` : "",
    claim.proof ? `Proof 节点数：${Array.isArray(claim.proof) ? claim.proof.length : 0}` : "",
    "完成链上 claim 后，把 txHash 发给我记录到 MonopolyFun。",
  ].filter(Boolean).join("\n");
}

function isNaturalDeliveryAcceptance(text) {
  const value = String(text ?? "").toLowerCase();
  return /(结果|交付|这个|成果).*(可以过|验收|通过)|(?:可以过|验收|通过).*(结果|交付|这个|成果)/.test(value);
}

function hasExplicitValidationScope(text) {
  return /验证证据|验证任务|这轮验证|验证\s*proof|validation\s*proof|validation\s*task/i.test(String(text ?? ""));
}

function summarizeWorkbench(workbench) {
  if (!workbench) {
    return null;
  }
  const items = Array.isArray(workbench.items) ? workbench.items : [];
  return {
    status: workbench.status,
    projectNo: workbench.projectNo ?? "",
    totalVisible: workbench.totalVisible ?? items.length,
    items: items.slice(0, 3).map((item) => ({
      id: item.id,
      title: item.title,
      reason: item.reason,
      actions: item.actions,
    })),
    error: workbench.error,
  };
}

function summarizeExecution(executed) {
  const result = executed?.result ?? {};
  return {
    status: executed?.status,
    action: executed?.action,
    projectNo: executed?.state?.projectNo ?? result.projectNo ?? "",
    itemId: result.item?.id ?? result.readback?.id ?? "",
    appealId: result.appeal?.id ?? result.appealId ?? "",
    workThreadId: result.workThreadId ?? result.workThread?.id ?? result.result?.workThreadId ?? "",
    period: result.period ?? result.distribution?.period ?? result.claim?.period ?? "",
    revenueClaimStatus: result.claim?.status ?? "",
    txHash: result.claim?.txHash ?? "",
    phase: result.phase ?? "",
    repoDelivery: result.repoDelivery ?? null,
    repoWorker: result.repoWorker ?? null,
    proactive: Array.isArray(result.proactive) ? result.proactive : [],
    state: executed?.state,
  };
}

function summarizeVerification(verified) {
  if (!verified) {
    return null;
  }
  if (verified.status === "unavailable") {
    return verified;
  }
  return {
    status: verified.status,
    action: verified.action,
    ok: verified.status === "ok",
  };
}

function resolveProjectNo(params, executed) {
  return String(
    executed?.state?.projectNo
      ?? executed?.result?.projectNo
      ?? params?.projectNo
      ?? "",
  );
}

function isWorkbenchNotice(text) {
  const value = text.toLowerCase();
  return value.includes("workbench") || value.includes("待办") || value.includes("通知");
}

function extractProjectNo(text) {
  return String(text).match(/MF\d+PRJ[0-9A-Z]+/i)?.[0] ?? "";
}

function parseJson(text) {
  try {
    return JSON.parse(String(text ?? ""));
  } catch {
    return null;
  }
}
