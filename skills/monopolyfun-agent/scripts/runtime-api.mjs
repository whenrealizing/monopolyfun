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
import { readFile } from "node:fs/promises";

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/runtime-api.mjs --method POST --path /api/v1/agent/turn --body '{\"intent\":\"view\",\"scene\":\"workbench\"}'",
    "",
    "runtime auth priority:",
    "  1. MONOPOLYFUN_HANDLE + MONOPOLYFUN_PASSWORD",
    "  2. MONOPOLYFUN_COOKIE + optional MONOPOLYFUN_CSRF",
    "",
    "options:",
    "  --method      http method, default GET",
    "  --path        monopolyfun api path",
    "  --body        json body string for write requests",
    "  --body-file   file that stores json body for write requests",
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
  const method = String(readOption(flags, "method", {
    defaultValue: "GET",
  })).toUpperCase();
  const path = readOption(flags, "path", { required: true });
  const rawBody = readOption(flags, "body");
  const bodyFile = readOption(flags, "body-file");
  // 中文注释：复杂请求体通过文件传入，避免 shell 转义破坏 JSON 结构。
  const body = await readJsonBody(rawBody, bodyFile);
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
  const result = await apiJson(runtime.session, baseUrl, method, path, body);
  printJson(result);
} catch (error) {
  printJson(buildFailurePayload(error, {
    status: "blocked",
    phase: "runtime_api",
  }));
  process.exit(1);
}

async function readJsonBody(rawBody, bodyFile) {
  if (rawBody && bodyFile) {
    throw new Error("use either --body or --body-file");
  }
  if (bodyFile) {
    return JSON.parse(await readFile(bodyFile, "utf8"));
  }
  return rawBody ? JSON.parse(rawBody) : undefined;
}
