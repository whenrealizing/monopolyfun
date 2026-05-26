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

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write([
    "usage: node scripts/verify-action.mjs --action create-project --project-no MF...",
    "options:",
    "  --action      action key",
    "  --project-no  project number, defaults to cached state",
    "  --item-id     work item id for workbench actions",
    "",
  ].join("\n") + "\n");
  process.exit(0);
}

try {
  const action = readOption(flags, "action", { required: true });
  const stateFile = readOption(flags, "state-file", {
    envKeys: ["MONOPOLYFUN_ACTION_STATE_FILE"],
    defaultValue: DEFAULT_STATE_FILE,
  });
  const state = await readState(stateFile);
  const projectNo = readOption(flags, "project-no", { defaultValue: state.projectNo });
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
  const verified = await verify(action, { projectNo, itemId: flags.get("item-id"), state }, runtime.session, baseUrl);
  printJson({ status: "ok", action, verified });
} catch (error) {
  printJson(buildFailurePayload(error, { phase: "verify_action" }));
  process.exit(1);
}

async function verify(action, input, session, baseUrl) {
  switch (action) {
    case "create-project":
      return {
        project: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.projectNo, "projectNo"))}`),
        dashboard: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(input.projectNo)}/dashboard`),
      };
    case "invite-role":
    case "accept-invite":
      return apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.projectNo, "projectNo"))}/roles`);
    case "claim-task":
    case "develop-task":
    case "submit-proof":
    case "review-proof":
    case "approve-share-release":
      if (action === "develop-task" && input.projectNo) {
        return {
          dashboard: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(input.projectNo)}/dashboard`),
          workbench: await apiJson(session, baseUrl, "GET", "/api/v1/workbench"),
          repoDeliverySessionId: input.state?.repoDeliverySessionId ?? "",
        };
      }
      if (input.itemId) {
        return apiJson(session, baseUrl, "GET", `/api/v1/work/items/${encodeURIComponent(input.itemId)}`);
      }
      return apiJson(session, baseUrl, "GET", "/api/v1/workbench");
    case "create-workthread":
    case "claim-workthread":
    case "submit-workthread-result":
    case "review-workthread":
      return {
        workroom: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.state?.projectId, "projectId"))}/workroom`),
        workThreadId: input.state?.workThreadId ?? "",
      };
    case "upsert-revenue-address":
    case "create-distribution":
    case "claim-revenue":
      return {
        workroom: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.state?.projectId, "projectId"))}/workroom`),
        rewards: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(input.state.projectId)}/rewards/me`),
      };
    case "create-validation-launch":
    case "publish-validation-launch":
    case "create-validation-task":
    case "claim-validation-task":
    case "submit-validation-proof":
    case "review-validation-proof":
    case "settle-validation-launch":
      return {
        launches: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.projectNo, "projectNo"))}/launches`),
        reviewQueue: await apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(input.projectNo)}/review-queue`),
      };
    case "create-feedback":
    case "resolve-feedback":
      return apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.projectNo, "projectNo"))}/validation-feedback`);
    case "bind-channel":
      return apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.projectNo, "projectNo"))}/channel-bindings`);
    case "archive-discussion":
      return apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.projectNo, "projectNo"))}/memory/source-contract`);
    case "create-appeal":
    case "resolve-appeal":
      return apiJson(session, baseUrl, "GET", `/api/v1/projects/${encodeURIComponent(required(input.projectNo, "projectNo"))}/appeals`);
    default:
      throw new Error(`unsupported action: ${action}`);
  }
}

function required(value, name) {
  if (value === undefined || value === null || String(value).trim() === "") {
    throw new Error(`${name} is required`);
  }
  return String(value).trim();
}

async function readState(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch {
    return {};
  }
}
