#!/usr/bin/env node

import {
  DEFAULT_BASE_URL,
  DEFAULT_HANDLE_FILE,
  DEFAULT_LOGIN_FILE,
  DEFAULT_SESSION_CACHE_FILE,
  apiJson,
  buildFailurePayload,
  formatHelp,
  parseArgs,
  printJson,
  readOption,
  resolveRuntimeAuth,
} from "./runtime-session.mjs";

const { args, flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/runtime-turn.mjs '{\"intent\":\"view\",\"scene\":\"workbench\"}'",
    "   or: node scripts/runtime-turn.mjs --input '{\"intent\":\"view\",\"scene\":\"workbench\"}'",
    "",
    "runtime auth priority:",
    "  1. MONOPOLYFUN_HANDLE + MONOPOLYFUN_PASSWORD",
    "  2. MONOPOLYFUN_COOKIE + optional MONOPOLYFUN_CSRF",
    "",
    "options:",
    "  --input       turn payload json string",
    "  --summary     print compact execution summary",
    "  --base-url    api base url, default http://host.docker.internal:8080",
    "  --handle      monopolyfun account handle",
    "  --password    monopolyfun account password",
    "  --handle-file file that stores monopolyfun account handle, default ~/.openclaw/credentials/monopolyfun-handle.txt",
    "  --login-file  file that stores monopolyfun login secret, default ~/.openclaw/credentials/monopolyfun-login.txt",
    "  --session-cache-file file that stores refreshed runtime cookies, default ~/.openclaw/monopolyfun/runtime-session.json",
    "  --cookie      cookie header",
    "  --csrf        csrf token",
  ]));
  process.exit(0);
}

try {
  const rawInput = readOption(flags, "input", {
    defaultValue: args[0],
  });
  if (!rawInput) {
    throw new Error("missing required turn input");
  }
  const input = JSON.parse(rawInput);
  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: DEFAULT_BASE_URL,
  });
  const handle = readOption(flags, "handle", {
    envKeys: ["MONOPOLYFUN_HANDLE"],
  });
  const handleFile = readOption(flags, "handle-file", {
    envKeys: ["MONOPOLYFUN_HANDLE_FILE"],
    defaultValue: DEFAULT_HANDLE_FILE,
  });
  const password = readOption(flags, "password", {
    envKeys: ["MONOPOLYFUN_PASSWORD", "MONOPOLYFUN_LOGIN_SECRET", "MONOPOLYFUN_LOGIN_VALUE"],
  });
  const loginFile = readOption(flags, "login-file", {
    envKeys: ["MONOPOLYFUN_LOGIN_FILE"],
    defaultValue: DEFAULT_LOGIN_FILE,
  });
  const sessionCacheFile = readOption(flags, "session-cache-file", {
    envKeys: ["MONOPOLYFUN_SESSION_CACHE_FILE"],
    defaultValue: DEFAULT_SESSION_CACHE_FILE,
  });
  const cookieHeader = readOption(flags, "cookie", {
    envKeys: ["MONOPOLYFUN_COOKIE"],
  });
  const csrfToken = readOption(flags, "csrf", {
    envKeys: ["MONOPOLYFUN_CSRF"],
  });
  const runtime = await resolveRuntimeAuth({
    baseUrl,
    handle,
    handleFile,
    password,
    loginFile,
    sessionCacheFile,
    cookieHeader,
    csrfToken,
  });
  const result = await apiJson(runtime.session, baseUrl, "POST", "/api/v1/agent/turn", input);
  printJson(flags.has("summary") ? summarizeTurn(result) : result);
} catch (error) {
  printJson(buildFailurePayload(error, {
    status: "blocked",
    phase: "runtime_turn",
  }));
  process.exit(1);
}

function summarizeTurn(result) {
  const projection = result?.projection && typeof result.projection === "object" ? result.projection : {};
  const current = projection.current && typeof projection.current === "object" ? projection.current : {};
  const summary = projection.summary && typeof projection.summary === "object" ? projection.summary : {};
  const counts = projection.counts && typeof projection.counts === "object" ? projection.counts : {};
  const actions = Array.isArray(result?.actions) ? result.actions : [];
  const lowRiskActions = actions.filter((action) => action?.requiresApproval !== true);
  const approvalRequiredActions = actions.filter((action) => action?.requiresApproval === true);
  // 中文注释：summary 模式只保留执行决策字段，适合写入 agent 运行记录。
  return {
    turnId: result?.turnId,
    scene: result?.scene,
    state: result?.state,
    subject: result?.subject,
    counts,
    summary,
    current,
    actions: actions.map((action) => ({
      id: action.id,
      label: action.label,
      riskCategory: action.riskCategory,
      requiresApproval: action.requiresApproval === true,
      operationId: action.apiOperation?.operationId,
      nextTurn: action.nextTurn,
    })),
    autopilot: {
      status: autopilotStatus(actions, lowRiskActions),
      noopReason: noopReason(actions, lowRiskActions),
      lowRiskActionIds: lowRiskActions.map((action) => action.id).filter(Boolean),
      approvalRequiredActionIds: approvalRequiredActions.map((action) => action.id).filter(Boolean),
      remainingWorkbenchCount: typeof counts.items === "number" ? counts.items : counts.count,
    },
    nextTurn: result?.nextTurn,
  };
}

function autopilotStatus(actions, lowRiskActions) {
  if (lowRiskActions.length > 0) {
    return "actionable";
  }
  return actions.length > 0 ? "noop" : "idle";
}

function noopReason(actions, lowRiskActions) {
  if (actions.length === 0) {
    return "empty_workbench";
  }
  return lowRiskActions.length === 0 ? "approval_required_only" : null;
}
