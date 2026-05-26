#!/usr/bin/env node

import {
  DEFAULT_BASE_URL,
  buildFailurePayload,
  formatHelp,
  parseArgs,
  printJson,
  readOption,
  resolveRuntimeAuth,
  runTurnHealthcheck,
} from "./runtime-session.mjs";

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/runtime-healthcheck.mjs [--base-url <url>] [--handle <handle>] [--password <password>] [--cookie <header>] [--csrf <token>]",
    "",
    "runtime auth priority:",
    "  1. MONOPOLYFUN_HANDLE + MONOPOLYFUN_PASSWORD",
    "  2. MONOPOLYFUN_COOKIE + optional MONOPOLYFUN_CSRF",
    "",
    "options:",
    "  --base-url    api base url, default http://host.docker.internal:8080",
    "  --handle      monopolyfun account handle",
    "  --password    monopolyfun account password",
    "  --session-cache-file file that stores refreshed runtime cookies",
    "  --cookie      cookie header",
    "  --csrf        csrf token",
    "",
    "env fallback:",
    "  MONOPOLYFUN_BASE_URL",
    "  MONOPOLYFUN_HANDLE",
    "  MONOPOLYFUN_PASSWORD",
    "  MONOPOLYFUN_LOGIN_SECRET",
    "  MONOPOLYFUN_LOGIN_VALUE",
    "  MONOPOLYFUN_HANDLE_FILE",
    "  MONOPOLYFUN_LOGIN_FILE",
    "  MONOPOLYFUN_SESSION_CACHE_FILE",
    "  MONOPOLYFUN_COOKIE",
    "  MONOPOLYFUN_CSRF",
  ]));
  process.exit(0);
}

try {
  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: DEFAULT_BASE_URL,
  });
  const handle = readOption(flags, "handle", {
    envKeys: ["MONOPOLYFUN_HANDLE"],
  });
  const handleFile = readOption(flags, "handle-file", {
    envKeys: ["MONOPOLYFUN_HANDLE_FILE"],
  });
  const password = readOption(flags, "password", {
    envKeys: ["MONOPOLYFUN_PASSWORD", "MONOPOLYFUN_LOGIN_SECRET", "MONOPOLYFUN_LOGIN_VALUE"],
  });
  const loginFile = readOption(flags, "login-file", {
    envKeys: ["MONOPOLYFUN_LOGIN_FILE"],
  });
  const sessionCacheFile = readOption(flags, "session-cache-file", {
    envKeys: ["MONOPOLYFUN_SESSION_CACHE_FILE"],
  });
  const cookieHeader = readOption(flags, "cookie", {
    envKeys: ["MONOPOLYFUN_COOKIE"],
  });
  const csrfToken = readOption(flags, "csrf", {
    envKeys: ["MONOPOLYFUN_CSRF"],
  });
  const runtime = await resolveRuntimeAuth({ baseUrl, handle, handleFile, password, loginFile, sessionCacheFile, cookieHeader, csrfToken });
  const healthcheck = await runTurnHealthcheck({ baseUrl, session: runtime.session });
  printJson({
    status: "ok",
    authMode: runtime.authMode,
    account: runtime.account,
    runtime: {
      cookiePresent: runtime.session.sessionCookiePresent(),
      csrfPresent: runtime.session.csrfCookiePresent(),
    },
    checks: {
      postTurnOk: healthcheck.postTurnOk,
      workbenchTurnOk: healthcheck.workbenchTurnOk,
    },
  });
} catch (error) {
  printJson(buildFailurePayload(error, {
    status: "blocked",
    phase: "runtime_healthcheck",
  }));
  process.exit(1);
}
