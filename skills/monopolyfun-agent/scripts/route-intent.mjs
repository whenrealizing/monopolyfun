#!/usr/bin/env node

import { parseArgs, printJson } from "./runtime-session.mjs";
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { actionDocPath } from "./project-action-registry.mjs";

const DEFAULT_STATE_FILE = join(homedir(), ".openclaw", "monopolyfun", "action-state.json");
const PROJECT_NO_PATTERN = /MF\d+PRJ[0-9A-Z]+/i;

const { args, flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write([
    "usage: node scripts/route-intent.mjs --text '<user message>'",
    "options:",
    "  --text        raw user message",
    "  --state-file  cached project context file",
    "",
  ].join("\n") + "\n");
  process.exit(0);
}

const text = String(flags.get("text") ?? args.join(" ")).trim();
const stateFile = String(flags.get("state-file") ?? process.env.MONOPOLYFUN_ACTION_STATE_FILE ?? DEFAULT_STATE_FILE);
const state = await readState(stateFile);
const route = routeIntent(text, state);

printJson({
  status: route.actionKey === "unknown" ? "needs_user_input" : "ok",
  userText: text,
  actionKey: route.actionKey,
  confidence: route.confidence,
  params: route.params,
  actionDoc: route.actionKey === "unknown" ? null : actionDocPath(route.actionKey),
  stateFile,
  executeCommand: route.actionKey === "unknown"
    ? null
    : `node scripts/execute-action.mjs --action ${route.actionKey} --params '${JSON.stringify(route.params)}'`,
});

function routeIntent(rawText, state) {
  const value = rawText.toLowerCase();
  const projectNo = rawText.match(PROJECT_NO_PATTERN)?.[0] ?? state.projectNo ?? "";
  const projectQuery = projectNo ? "" : extractProjectQuery(rawText, state);

  if (value.includes("workbench") || value.includes("待办") || value.includes("通知")) {
    return routed("workbench-current", 0.86, { projectNo, projectQuery });
  }
  if (matchAny(value, ["创建", "创办", "新建", "create", "开一个项目", "开个项目"])
    && matchAny(value, ["项目", "project", "monopolyfun"])
    && !matchAny(value, ["验证", "validation", "反馈", "feedback", "workthread", "work thread", "工作线程", "收益领取", "分账", "测试链", "txhash", "bounty", "赏金"])) {
    return routed("create-project", 0.9, {
      title: extractTitle(rawText) ?? `QA Project ${new Date().toISOString().slice(0, 10)}`,
      taskName: extractTaskName(rawText) ?? "QA 验证任务",
    });
  }
  if (matchAny(value, ["邀请", "加入", "参与", "负责", "权限", "负责人"]) && matchAny(value, ["dev", "cto", "技术", "reviewer", "coo", "验收", "权限", "负责人"])) {
    return routed("invite-role", 0.86, {
      projectNo,
      account: extractAccount(rawText),
      roleCode: inferRoleCode(rawText),
    });
  }
  if (matchAny(value, ["接受", "同意", "accept"]) && matchAny(value, ["邀请", "invite", "项目"])) {
    return routed("accept-invite", 0.84, { projectNo, roleCode: inferRoleCode(rawText) });
  }
  // 中文注释：shares 发放属于高风险审批入口，独立路由可以让 OpenClaw 在报告里暴露权限边界和审批证据。
  if (isShareReleaseApprovalText(value)) {
    return routed("approve-share-release", 0.82, {
      projectNo,
      reason: rawText,
    });
  }
  if (matchAny(value, ["发布", "publish", "放出去", "可以开始"]) && matchAny(value, ["验证", "validation", "轮次", "launch", "那轮"])) {
    return routed("publish-validation-launch", 0.8, {
      projectNo,
      reason: rawText,
    });
  }
  if (matchAny(value, ["创建验证任务", "新建验证任务", "validation task", "反馈转任务", "转任务", "补一个验收任务", "补个验收任务"])) {
    return routed("create-validation-task", 0.78, {
      projectNo,
      title: extractTitle(rawText) ?? "OpenClaw 验证任务",
      intent: rawText,
      deliverable: "提交可验证结果、链接和结论。",
    });
  }
  if (matchAny(value, ["领取", "claim", "开工", "接任务", "接一个", "想接", "分给我", "能做"]) && matchAny(value, ["验证任务", "validation task", "这轮验证", "验证相关"])) {
    return routed("claim-validation-task", 0.78, {
      projectNo,
      projectQuery,
      reason: rawText,
    });
  }
  if (isValidationProofReviewText(value)) {
    return routed("review-validation-proof", 0.8, {
      projectNo,
      projectQuery,
      decision: inferValidationReviewDecision(value),
      reason: rawText,
    });
  }
  if (isReviewWorkThreadText(value)) {
    return routed("review-workthread", 0.84, {
      projectNo,
      projectQuery,
      workThreadId: extractWorkThreadId(rawText),
      decision: inferWorkThreadDecision(value),
      reason: rawText,
    });
  }
  if (matchAny(value, ["提交", "submit", "上传", "做完", "完成"]) && matchAny(value, ["验证证据", "submit validation proof", "验证 proof", "validation proof", "这轮验证"])) {
    return routed("submit-validation-proof", 0.8, {
      projectNo,
      projectQuery,
      proofUrl: extractUrl(rawText),
      summary: rawText,
    });
  }
  if (matchAny(value, ["反馈", "feedback", "问题", "bug"]) && matchAny(value, ["处理", "解决", "resolve", "关闭", "dismiss", "关掉"])) {
    return routed("resolve-feedback", 0.76, {
      projectNo,
      resolution: rawText,
    });
  }
  if (matchAny(value, ["反馈", "feedback", "bug", "建议"]) && !matchAny(value, ["创建验证轮次", "新建验证轮次", "validation launch", "验证轮次", "一轮验证"])) {
    return routed("create-feedback", 0.76, {
      projectNo,
      intent: rawText,
      reason: rawText,
    });
  }
  if (matchAny(value, ["创建验证", "新建验证", "validation launch", "验证轮次", "验证列表", "开一轮验证", "开一轮"])) {
    return routed("create-validation-launch", 0.8, {
      projectNo,
      title: extractTitle(rawText) ?? "OpenClaw 验证轮次",
      hypothesis: rawText,
    });
  }
  if (matchAny(value, ["结算验证", "结算奖励", "settle validation", "settle rewards", "reward settle", "结一下", "奖励"])) {
    return routed("settle-validation-launch", 0.8, {
      projectNo,
      reason: rawText,
    });
  }
  if (matchAny(value, ["反馈", "feedback", "问题", "bug"]) && matchAny(value, ["处理", "解决", "resolve", "关闭", "dismiss"])) {
    return routed("resolve-feedback", 0.76, {
      projectNo,
      resolution: rawText,
    });
  }
  // 中文注释：小白用户通常只说公司名和“做好发链接”，在验证/反馈/显式 proof 场景之后再进入自主开发链路。
  if (isAutonomousDevelopmentText(value)) {
    return routed("develop-task", 0.88, {
      projectNo,
      projectQuery: extractProjectQuery(rawText, state),
      taskQuery: extractTaskQuery(rawText),
      userRequest: rawText,
      reportMode: "proactive",
      prUrl: extractUrl(rawText),
      headCommit: extractCommit(rawText),
    });
  }
  if (isRevenueAddressText(value)) {
    return routed("upsert-revenue-address", 0.84, {
      projectNo,
      projectQuery,
      chainId: extractChainId(rawText),
      contractAddress: extractAddressAfter(rawText, ["contractAddress", "contract", "收益合约地址", "合约地址", "分账合约", "收益合约"]),
      tokenAddress: extractAddressAfter(rawText, ["tokenAddress", "token", "代币地址", "代币", "usdc", "USDC"]) || extractEthereumAddress(rawText, 1),
    });
  }
  if (isCreateWorkThreadText(value)) {
    return routed("create-workthread", 0.86, {
      projectNo,
      projectQuery,
      title: extractTitle(rawText) ?? extractTaskName(rawText) ?? "OpenClaw 收益领取验证任务",
      goal: extractGoal(rawText) || rawText,
      deliverables: extractListAfter(rawText, ["交付物", "deliverables"]) || ["PR 或执行报告", "测试摘要", "收益领取证据"],
      acceptanceCriteria: extractListAfter(rawText, ["验收标准", "acceptance"]) || ["结果可以复核", "收益领取证据可以读回"],
      taskValue: extractIntegerAfter(rawText, ["taskValue", "价值", "权重"]) ?? 5000,
      bountyAmountMinor: extractIntegerAfter(rawText, ["bountyAmountMinor", "赏金", "bounty"]) ?? 0,
      bountyToken: extractToken(rawText) || "BNB",
      repoRef: extractRepoRef(rawText),
      issueUrl: extractUrl(rawText),
    });
  }
  if (isClaimWorkThreadText(value)) {
    return routed("claim-workthread", 0.84, {
      projectNo,
      projectQuery,
      workThreadId: extractWorkThreadId(rawText),
      reason: rawText,
    });
  }
  if (isSubmitWorkThreadResultText(value)) {
    return routed("submit-workthread-result", 0.84, {
      projectNo,
      projectQuery,
      workThreadId: extractWorkThreadId(rawText),
      summary: rawText,
      prUrl: extractUrl(rawText),
      testSummary: extractTestSummary(rawText),
      changedFiles: extractChangedFiles(rawText),
      evidenceRefs: extractEvidenceRefs(rawText),
    });
  }
  if (isClaimRevenueText(value)) {
    return routed("claim-revenue", 0.88, {
      projectNo,
      projectQuery,
      period: extractPeriod(rawText),
      walletAddress: extractEthereumAddress(rawText, 0),
      txHash: extractTxHash(rawText),
    });
  }
  if (isCreateDistributionText(value)) {
    return routed("create-distribution", 0.84, {
      projectNo,
      projectQuery,
      period: extractPeriod(rawText),
      totalRevenueMinor: extractIntegerAfter(rawText, ["totalRevenueMinor", "收入", "收益", "revenue", "amount"]) ?? extractFirstInteger(rawText),
    });
  }
  if (matchAny(value, ["开始", "领取", "开工", "claim", "接任务", "接一个", "想接", "分给我"])) {
    return routed("claim-task", 0.82, { projectNo, projectQuery });
  }
  // 中文注释：申诉语义优先于“验收结果”里的验收字样，避免把创建申诉误路由成再次验收。
  if (matchAny(value, ["申诉", "appeal"])) {
    if (matchAny(value, ["处理", "解决", "resolve", "结论"])) {
      return routed("resolve-appeal", 0.78, {
        projectNo,
        resolution: rawText,
      });
    }
    return routed("create-appeal", 0.78, {
      projectNo,
      reason: rawText,
    });
  }
  // 中文注释：带提交动词和 proof 证据的文本优先按提交处理，防止 proof 正文里的 accepted 等词触发验收。
  if (isSubmitProofText(value)) {
    return routed("submit-proof", 0.84, {
      projectNo,
      projectQuery,
      proofUrl: extractUrl(rawText),
      summary: rawText,
    });
  }
  // 中文注释：验收方要求补证或风险挂起时仍属于 review-proof，避免落到提交 proof 或未知动作。
  if (isReviewHoldText(value)) {
    return routed("review-proof", 0.8, {
      projectNo,
      projectQuery,
      decision: "hold",
      reason: rawText,
    });
  }
  if (isReviewRevisionText(value)) {
    return routed("review-proof", 0.8, {
      projectNo,
      projectQuery,
      decision: "request_changes",
      reason: rawText,
    });
  }
  if (isDeliveryReviewText(value)) {
    return routed("review-proof", 0.82, {
      projectNo,
      projectQuery,
      decision: "accepted",
      reason: rawText,
    });
  }
  if (matchAny(value, ["proof", "证明", "完成", "提交", "交付"])) {
    return routed("submit-proof", 0.82, {
      projectNo,
      projectQuery,
      proofUrl: extractUrl(rawText),
      summary: rawText,
    });
  }
  if (matchAny(value, ["绑定", "channel", "通道", "协作记录", "l1", "l2"])) {
    return routed("bind-channel", 0.8, {
      projectNo,
      bindings: extractBindings(rawText),
    });
  }
  if (matchAny(value, ["归档", "archive", "讨论", "同步", "归进去", "归到"])) {
    return routed("archive-discussion", 0.8, {
      projectNo,
      externalRef: extractExternalRef(rawText),
      body: rawText,
    });
  }
  return routed("unknown", 0.2, { projectNo });
}

function routed(actionKey, confidence, params) {
  return { actionKey, confidence, params };
}

function matchAny(value, terms) {
  return terms.some((term) => value.includes(term));
}

function isReviewHoldText(value) {
  return matchAny(value, ["挂起", "复核中", "hold", "风险", "risk"]) && matchAny(value, ["验收", "验证", "proof", "结果", "证据", "交付", "任务"]);
}

function isReviewRevisionText(value) {
  return matchAny(value, ["要求补充", "需要补充", "需补充", "缺少", "退回", "返工", "request changes", "request_changes"])
    && matchAny(value, ["验收", "验证", "proof", "结果", "证据", "交付", "任务", "截图", "链接", "结论"]);
}

function isShareReleaseApprovalText(value) {
  return matchAny(value, ["share release", "shares release", "股份发放", "股份释放", "份额释放", "shares 释放", "share 释放", "批准虚拟股份", "approve_share_release"])
    && matchAny(value, ["审批", "批准", "通过", "approve", "release", "发放", "释放", "批掉"]);
}

function isSubmitProofText(value) {
  return matchAny(value, ["提交", "交付", "submit", "upload", "做完", "完成"])
    && matchAny(value, ["proof", "证明", "证据", "链接", "http://", "https://"]);
}

function isAutonomousDevelopmentText(value) {
  if (matchAny(value, ["workthread", "work thread", "工作线程", "收益领取", "测试链", "txhash", "分账", "收益分配", "领取收益", "收益合约", "合约地址", "代币地址", "chainid", "tokenaddress", "contractaddress"])) {
    return false;
  }
  if (matchAny(value, ["发个任务", "发一个任务", "发布任务", "任务给开发", "找开发同学"])) {
    return false;
  }
  const implementationVerb = matchAny(value, [
    "处理",
    "做一下",
    "做了",
    "弄好",
    "开发",
    "改代码",
    "fix",
    "implement",
    "build",
    "ship",
  ]);
  const delegatedWithLink = matchAny(value, ["帮", "给", "麻烦"]) && matchAny(value, ["链接", "pr", "pull request", "预览", "preview", "发我", "告诉我"]);
  const wantsDeliveryLink = matchAny(value, ["链接", "pr", "pull request", "预览", "preview", "push", "发我", "告诉我"]);
  const mentionsTask = matchAny(value, ["任务", "前端", "验收", "代码", "bug", "页面", "功能", "project", "项目"]);
  const repoProof = hasRepoProofSignal(value);
  if (matchAny(value, ["验证证据", "validation proof", "验证 proof", "这轮验证", "反馈"])) {
    return false;
  }
  return (implementationVerb || delegatedWithLink) && (wantsDeliveryLink || mentionsTask) && (!isSubmitProofText(value) || repoProof);
}

function hasRepoProofSignal(value) {
  return /\bpr\b|pull request|commit|headcommit|head commit/i.test(value);
}

function inferValidationReviewDecision(value) {
  if (matchAny(value, ["退回", "补充", "request", "changes"])) {
    return "request_changes";
  }
  if (matchAny(value, ["挂起", "hold", "风险"])) {
    return "hold";
  }
  return "accept";
}

function isValidationProofReviewText(value) {
  const reviewIntent = matchAny(value, ["复核", "审核", "review", "通过", "退回", "挂起", "可以过", "能过"]);
  if (!reviewIntent) {
    return false;
  }
  const evidenceBundle = !value.includes("proof") && matchAny(value, ["链接", "截图"]) && matchAny(value, ["结论", "ci"]);
  if (evidenceBundle) {
    return true;
  }
  // 中文注释：验证复核只接收显式验证语义，避免“结果可以过”这类普通交付验收被误判为 validation proof。
  return matchAny(value, ["验证证据", "validation proof", "验证 proof", "验证任务", "这轮验证", "验证结果", "验证结论"]);
}

function isDeliveryReviewText(value) {
  const reviewIntent = matchAny(value, ["通过", "验收", "approve", "accept", "可以过", "能过"]);
  const deliverySubject = matchAny(value, ["任务", "交付", "proof", "检查", "结果", "成果", "这个", "这个结果"]);
  return reviewIntent && deliverySubject;
}

function isRevenueAddressText(value) {
  return matchAny(value, ["收益地址", "分账合约", "收益合约", "revenue address", "contractaddress", "tokenaddress"])
    && matchAny(value, ["绑定", "设置", "配置", "upsert", "接入", "接下", "接一下", "接上", "填"]);
}

function isCreateWorkThreadText(value) {
  const creation = matchAny(value, ["创建", "新建", "发一个", "发个", "开一个", "开个", "安排", "派给", "找人", "找开发", "找个开发", "处理第一件", "做第一件", "create"]);
  const taskContext = matchAny(value, ["任务", "活", "事情", "第一件事", "第一件", "这件事", "workthread", "work thread"]);
  const payoutContext = matchAny(value, ["收益领取", "领钱", "拿钱", "分账", "测试链", "txhash", "余额", "bounty", "赏金", "收入"]);
  // 中文注释：真实 owner 会说“找人把拿钱跑通”，这里把自然任务表达映射到 WorkThread 创建。
  return creation && taskContext && (payoutContext || matchAny(value, ["dev", "开发", "同学", "别人"]));
}

function isClaimWorkThreadText(value) {
  if (matchAny(value, ["收益合约", "合约地址", "代币", "token", "chainid", "contractaddress"])) {
    return false;
  }
  const claim = matchAny(value, ["领取", "接任务", "接的任务", "有没有适合", "接这个", "开工", "claim", "我来做", "我接", "能做", "有什么可以做", "有什么任务", "任务我可以接", "可以接吗", "任务能接", "有啥任务", "能接的活", "有啥能接"]);
  const context = matchAny(value, ["workthread", "work thread", "工作线程", "收益领取", "测试链", "txhash", "分账任务", "领钱", "拿钱", "收益", "任务"]);
  const revenueClaimDetail = matchAny(value, ["钱包", "wallet", "txhash", "tx hash"]);
  return claim && context && !revenueClaimDetail;
}

function isSubmitWorkThreadResultText(value) {
  const submit = matchAny(value, ["提交结果", "提交交付", "提交收益", "交付一个结果", "交结果", "做完", "完成了", "做好了", "搞定了", "submit result", "result"]);
  const context = matchAny(value, ["workthread", "work thread", "工作线程", "收益领取", "收益", "测试链", "txhash", "余额", "pr", "pull request", "证据", "报告", "结果"]);
  return submit && context;
}

function isReviewWorkThreadText(value) {
  const review = matchAny(value, ["验收", "通过", "接受", "接不接受", "可以过", "accept", "review"]);
  const context = matchAny(value, ["workthread", "work thread", "工作线程", "wt-", "收益领取", "txhash", "余额变化", "结果", "交付", "dev", "整体没问题"]);
  return review && context;
}

function isCreateDistributionText(value) {
  const creation = matchAny(value, ["创建分配", "发布", "收益分配", "分配收益", "创建收益", "create distribution", "distribution", "分账", "发收益", "收益发", "发一下", "结算"]);
  const context = matchAny(value, ["收益", "revenue", "period", "月份", "分配", "分账", "钱", "收入"]);
  return creation && context;
}

function isClaimRevenueText(value) {
  const workThreadTask = matchAny(value, ["workthread", "work thread", "工作线程"]);
  const explicitClaimDetail = matchAny(value, ["钱包", "wallet", "txhash", "tx hash", "claim revenue", "claim reward", "能领", "提收益", "领钱", "拿钱"]);
  // 中文注释：用户回填链上交易时通常只发“交易完成 + txHash”，这里直接续接收益 claim 记录。
  return matchAny(value, ["领取收益", "收益领取", "claim revenue", "claim reward", "能领", "提收益", "钱包", "收益 claim", "领钱", "拿钱", "txhash", "tx hash", "交易完成", "到账"])
    && (!workThreadTask || explicitClaimDetail);
}

function extractTitle(text) {
  const match = text.match(/(?:项目名称|项目名|title|name)\s*(?:是|为|:|：)\s*([^。,\n，]+)/i);
  if (match) {
    return match[1].trim();
  }
  const naturalName = text.match(/(?:名字叫|叫做|叫)\s*([^。,\n，]+)/i);
  if (naturalName) {
    return naturalName[1].trim();
  }
  const projectMatch = text.match(/(?:创建|创办|新建).{0,8}(?:项目|project)\s*([^。,\n，]*)/i);
  const candidate = projectMatch?.[1]?.trim();
  return candidate || null;
}

function extractTaskName(text) {
  const match = text.match(/(?:任务名称|任务名|初始化任务)\s*(?:是|为|:|：)\s*([^。,\n，]+)/i);
  return match?.[1]?.trim() || null;
}

function extractGoal(text) {
  return text.match(/(?:目标|goal)\s*(?:是|为|:|：)\s*([^。\n]+)/i)?.[1]?.trim() || "";
}

function extractListAfter(text, labels) {
  for (const label of labels) {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = text.match(new RegExp(`${escaped}\\s*(?:是|为|:|：)\\s*([^。\\n]+)`, "i"));
    if (match?.[1]) {
      return match[1]
        .split(/[、,，;]/)
        .map((item) => item.trim())
        .filter(Boolean);
    }
  }
  return null;
}

function extractIntegerAfter(text, labels) {
  for (const label of labels) {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = text.match(new RegExp(`${escaped}\\s*(?:是|为|:|：)?\\s*(\\d+)`, "i"));
    if (match?.[1]) {
      return Number.parseInt(match[1], 10);
    }
  }
  return null;
}

function extractFirstInteger(text) {
  const matches = [...String(text ?? "").matchAll(/\b\d+\b/g)].map((match) => Number.parseInt(match[0], 10));
  return matches.length > 0 ? matches[matches.length - 1] : null;
}

function extractToken(text) {
  return text.match(/\b(BNB|USDC|USDT|ETH|DAI)\b/i)?.[1]?.toUpperCase() || "";
}

function extractRepoRef(text) {
  return text.match(/[a-z0-9_.-]+\/[a-z0-9_.-]+(?:\/[a-z0-9_.-]+)?/i)?.[0] || "";
}

function extractWorkThreadId(text) {
  return text.match(/\bwt-[a-z0-9-]+\b/i)?.[0]
    || text.match(/\bworkthread[_:-]?([a-z0-9-]{6,})\b/i)?.[1]
    || "";
}

function extractTestSummary(text) {
  return text.match(/(?:测试|test)\s*(?:是|为|:|：)\s*([^。\n]+)/i)?.[1]?.trim() || "";
}

function extractChangedFiles(text) {
  const files = [...text.matchAll(/[a-z0-9_.-]+\/[a-z0-9_./-]+\.[a-z0-9]+/gi)].map((match) => match[0]);
  return [...new Set(files)];
}

function extractEvidenceRefs(text) {
  const refs = [];
  const url = extractUrl(text);
  if (url) {
    refs.push(url);
  }
  const txHash = extractTxHash(text);
  if (txHash) {
    refs.push(txHash);
  }
  return refs;
}

function extractPeriod(text) {
  return text.match(/\b(20\d{2}-\d{2})\b/)?.[1] || "";
}

function extractChainId(text) {
  return text.match(/(?:chainId|chain id|链 id|链ID)\s*(?:是|为|=|:|：)?\s*(eip155:\d+|\d+)/i)?.[1]
    || text.match(/\beip155:\d+\b/i)?.[0]
    || "";
}

function extractEthereumAddress(text, index = 0) {
  const matches = [...text.matchAll(/0x[a-fA-F0-9]{40}(?![a-fA-F0-9])/g)].map((match) => match[0]);
  return matches[index] || "";
}

function extractAddressAfter(text, labels) {
  for (const label of labels) {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const match = text.match(new RegExp(`${escaped}\\s*(?:是|为|=|:|：)?\\s*(0x[a-fA-F0-9]{40})`, "i"));
    if (match?.[1]) {
      return match[1];
    }
  }
  return "";
}

function extractTxHash(text) {
  return text.match(/0x[a-fA-F0-9]{64}/)?.[0] || "";
}

function inferWorkThreadDecision(value) {
  if (matchAny(value, ["拒绝", "reject", "退回", "补充", "request changes"])) {
    return "reject";
  }
  return "accept";
}

function extractAccount(text) {
  const at = text.match(/@?([a-zA-Z][a-zA-Z0-9_-]{1,40})/);
  if (!at) {
    return "";
  }
  const candidate = at[1];
  return ["MF", "PRJ", "project", "monopolyfun"].some((prefix) => candidate.toLowerCase().startsWith(prefix.toLowerCase()))
    ? ""
    : candidate;
}

function inferRoleCode(text) {
  const value = text.toLowerCase();
  if (matchAny(value, ["ceo", "负责人", "最高权限", "治理"])) {
    return "system_ceo";
  }
  if (matchAny(value, ["reviewer", "coo", "验收", "审核"])) {
    return "system_coo";
  }
  if (matchAny(value, ["dev", "cto", "技术", "开发", "交付"])) {
    return "system_cto";
  }
  return "";
}

function extractUrl(text) {
  return text.match(/https?:\/\/[^\s。)，,，]+/)?.[0] ?? "";
}

function extractCommit(text) {
  return text.match(/\b[0-9a-f]{7,40}\b/i)?.[0] ?? "";
}

function extractProjectQuery(text, state = {}) {
  const explicit = String(state.projectQuery ?? "").trim();
  if (explicit) {
    return explicit;
  }
  const projectNo = text.match(PROJECT_NO_PATTERN)?.[0];
  if (projectNo) {
    return projectNo;
  }
  const beforeBa = text.match(/(?:帮|给|请|麻烦)?\s*([A-Za-z0-9_\-\u4e00-\u9fa5]{2,40})\s*把/i);
  if (beforeBa?.[1]) {
    return cleanupNaturalToken(beforeBa[1]);
  }
  const beforeThis = text.match(/^([A-Za-z0-9_\-\u4e00-\u9fa5]{2,40})\s*(?:这个|那个|这边|那边)/i);
  if (beforeThis?.[1]) {
    return cleanupNaturalToken(beforeThis[1]);
  }
  const beforeTask = text.match(/(?:帮|把|给|处理|看下|看看)?\s*([A-Za-z0-9_\-\u4e00-\u9fa5]{2,40})\s*(?:的|那个|这个)?\s*(?:项目|前端|代码|任务|验收|bug|页面|功能)/i);
  if (beforeTask?.[1]) {
    return cleanupNaturalToken(beforeTask[1]);
  }
  const beforeDelivery = text.match(/([A-Za-z0-9_\-\u4e00-\u9fa5]{2,40})\s*(?:那边|那个|这个|项目).{0,12}(?:做|开发|处理|修|发)/i);
  return beforeDelivery?.[1] ? cleanupNaturalToken(beforeDelivery[1]) : "";
}

function extractTaskQuery(text) {
  const match = text.match(/(?:把|做|处理|修|开发|完成)?\s*([A-Za-z0-9_\-\u4e00-\u9fa5]{2,60}?(?:任务|前端|验收|bug|页面|功能|代码))[，。,\s]/i);
  return match?.[1] ? cleanupNaturalToken(match[1]) : "";
}

function cleanupNaturalToken(value) {
  const cleaned = String(value ?? "")
    .replace(/^(帮|把|给|请|麻烦|OpenClaw|openclaw)/i, "")
    .replace(/(那个|这个|项目|公司)$/i, "")
    .trim();
  if (["我", "我们", "你", "你们", "帮我"].includes(cleaned)) {
    return "";
  }
  if (/今天|有啥|有没有|什么|任务能接|可以接/.test(cleaned)) {
    return "";
  }
  return cleaned;
}

function extractExternalRef(text) {
  return extractUrl(text)
    || text.match(/[a-z0-9_.-]+\/[a-z0-9_.-]+#[a-z0-9_.-]+/i)?.[0]
    || text.match(/[a-z0-9_.+-]+@[a-z0-9_.-]+/i)?.[0]
    || "monopolyfun-project-inbox";
}

function extractBindings(text) {
  const refs = [...text.matchAll(/(github|issue|email|mail|feishu|slack|discord)?\s*([a-z0-9_.+-]+\/[a-z0-9_.-]+#[a-z0-9_.-]+|[a-z0-9_.+-]+@[a-z0-9_.-]+|https?:\/\/\S+)/gi)]
    .map((match) => match[2].replace(/[。),，]+$/g, ""));
  const bindings = [];
  if (refs[0]) {
    bindings.push(binding("L1", "github", "issue", refs[0]));
  }
  if (refs[1]) {
    bindings.push(binding("L2", "email", "thread", refs[1]));
  }
  return bindings;
}

function binding(level, provider, channelType, externalRef) {
  return { level, provider, channelType, externalRef, displayName: `${provider}:${externalRef}` };
}

async function readState(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch {
    return {};
  }
}
