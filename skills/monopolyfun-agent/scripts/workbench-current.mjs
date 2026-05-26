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
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";

const DEFAULT_STATE_FILE = join(homedir(), ".openclaw", "monopolyfun", "action-state.json");
const DEFERRED_REASONS = new Set(["project_initiative_recommendation"]);

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write([
    "usage: node scripts/workbench-current.mjs --project-no MF...",
    "options:",
    "  --project-no  project business number, defaults to cached state",
    "  --limit       max visible items, default 3",
    "",
  ].join("\n") + "\n");
  process.exit(0);
}

try {
  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: DEFAULT_BASE_URL,
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
  const stateFile = readOption(flags, "state-file", {
    envKeys: ["MONOPOLYFUN_ACTION_STATE_FILE"],
    defaultValue: DEFAULT_STATE_FILE,
  });
  const state = await readState(stateFile);
  const projectNo = readOption(flags, "project-no", { defaultValue: state.projectNo });
  const limit = Number(readOption(flags, "limit", { defaultValue: "3" }));
  const items = await apiJson(runtime.session, baseUrl, "GET", "/api/v1/workbench");
  const visible = normalizeItems(items)
    .filter((item) => !DEFERRED_REASONS.has(item.reason))
    .filter((item) => !projectNo || itemMatchesProject(item, projectNo))
    .slice(0, Number.isFinite(limit) && limit > 0 ? limit : 3)
    .map(summarizeItem);

  // 中文注释：只输出当前项目立即可处理事项，降低 OpenClaw 每轮读取整队列的上下文成本。
  printJson({
    status: "ok",
    projectNo: projectNo || "",
    totalVisible: visible.length,
    items: visible,
  });
} catch (error) {
  printJson(buildFailurePayload(error, { phase: "workbench_current" }));
  process.exit(1);
}

export function normalizeItems(input) {
  if (Array.isArray(input)) {
    return input.map(normalizeItem);
  }
  if (Array.isArray(input?.items)) {
    return input.items.map(normalizeItem);
  }
  if (Array.isArray(input?.content)) {
    return input.content.map(normalizeItem);
  }
  return [];
}

export function itemMatchesProject(item, projectNo) {
  const text = JSON.stringify(item).toLowerCase();
  return text.includes(String(projectNo).toLowerCase());
}

export function itemHasAction(item, actionId) {
  return item.actions.some((action) => action.id === actionId);
}

export function summarizeItem(item) {
  return {
    id: item.id,
    title: item.title,
    reason: item.reason,
    target: item.target,
    requiredRoleCode: item.requiredRoleCode,
    actions: item.actions.map((action) => action.id),
    nextTurn: item.nextTurn,
  };
}

function normalizeItem(item) {
  return {
    ...item,
    id: String(item?.id ?? item?.itemId ?? ""),
    title: String(item?.title ?? ""),
    reason: String(item?.reason ?? ""),
    target: item?.target ?? {},
    requiredRoleCode: item?.requiredRoleCode ?? item?.required_role_code ?? "",
    actions: Array.isArray(item?.actions)
      ? item.actions.map((action) => typeof action === "string" ? { id: action } : { ...action, id: String(action?.id ?? "") })
      : [],
    nextTurn: item?.nextTurn ?? null,
  };
}

async function readState(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch {
    return {};
  }
}
