#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import {
  DEFAULT_BASE_URL,
  buildFailurePayload,
  formatHelp,
  parseArgs,
  printJson,
  readOption,
  registerOrLogin,
  runTurnHealthcheck,
} from "./runtime-session.mjs";

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/runtime-bootstrap.mjs --handle <handle> --password <password> [--base-url <url>]",
    "",
    "options:",
    "  --handle      monopolyfun account handle",
    "  --password    monopolyfun account password",
    "  --base-url    api base url, default http://host.docker.internal:8080",
    "  --secret-source       OpenClaw SecretRef source, default env",
    "  --secret-provider     OpenClaw SecretRef provider, default default",
    "  --password-secret-id  OpenClaw secret id for password",
    "  --cookie-secret-id    OpenClaw secret id for cookie header",
    "  --csrf-secret-id      OpenClaw secret id for csrf token",
    "",
    "env fallback:",
    "  MONOPOLYFUN_HANDLE",
    "  MONOPOLYFUN_PASSWORD",
    "  MONOPOLYFUN_LOGIN_SECRET",
    "  MONOPOLYFUN_LOGIN_VALUE",
    "  MONOPOLYFUN_HANDLE_FILE",
    "  MONOPOLYFUN_LOGIN_FILE",
    "  MONOPOLYFUN_BASE_URL",
    "  MONOPOLYFUN_SECRET_SOURCE",
    "  MONOPOLYFUN_SECRET_PROVIDER",
    "  MONOPOLYFUN_PASSWORD_SECRET_ID",
    "  MONOPOLYFUN_COOKIE_SECRET_ID",
    "  MONOPOLYFUN_CSRF_SECRET_ID",
  ]));
  process.exit(0);
}

async function readOptionalFile(filePath) {
  const trimmed = filePath?.trim?.() ?? "";
  if (!trimmed) {
    return "";
  }
  return (await readFile(trimmed, "utf8")).trim();
}

try {
  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: DEFAULT_BASE_URL,
  });
  const handle = readOption(flags, "handle", {
    envKeys: ["MONOPOLYFUN_HANDLE"],
    required: true,
  });
  const passwordDirect = readOption(flags, "password", {
    envKeys: ["MONOPOLYFUN_PASSWORD", "MONOPOLYFUN_LOGIN_SECRET", "MONOPOLYFUN_LOGIN_VALUE"],
    defaultValue: "",
  });
  const loginFile = readOption(flags, "login-file", {
    envKeys: ["MONOPOLYFUN_LOGIN_FILE"],
    defaultValue: "",
  });
  // 中文注释：OpenClaw 自动注册会把密码落到受限文件，bootstrap 优先读文件来避免把明文写入配置。
  const password = passwordDirect || await readOptionalFile(loginFile);
  if (!password) {
    throw new Error("missing required option: password");
  }
  const secretSource = readOption(flags, "secret-source", {
    envKeys: ["MONOPOLYFUN_SECRET_SOURCE"],
    defaultValue: "env",
  });
  const secretProvider = readOption(flags, "secret-provider", {
    envKeys: ["MONOPOLYFUN_SECRET_PROVIDER"],
    defaultValue: "default",
  });
  const passwordSecretId = readOption(flags, "password-secret-id", {
    envKeys: ["MONOPOLYFUN_PASSWORD_SECRET_ID"],
    defaultValue: "MONOPOLYFUN_PASSWORD",
  });
  const cookieSecretId = readOption(flags, "cookie-secret-id", {
    envKeys: ["MONOPOLYFUN_COOKIE_SECRET_ID"],
    defaultValue: "MONOPOLYFUN_COOKIE",
  });
  const csrfSecretId = readOption(flags, "csrf-secret-id", {
    envKeys: ["MONOPOLYFUN_CSRF_SECRET_ID"],
    defaultValue: "MONOPOLYFUN_CSRF",
  });

  const auth = await registerOrLogin({ baseUrl, handle, password });
  const healthcheck = await runTurnHealthcheck({
    baseUrl,
    session: auth.session,
  });
  // 中文注释：bootstrap 输出直接给外部运行时消费，运行时据此写入 public env、secret store 和 ready 状态。
  printJson({
    status: "ready",
    registrationMode: auth.mode,
    account: {
      accountId: auth.account.id,
      handle: auth.account.handle,
      displayName: auth.account.displayName,
    },
    publicEnv: {
      MONOPOLYFUN_BASE_URL: baseUrl,
    },
    secrets: {
      [passwordSecretId]: password,
      [cookieSecretId]: auth.session.headerValue(),
      [csrfSecretId]: auth.session.csrfToken(),
    },
    secretBindings: {
      MONOPOLYFUN_COOKIE: cookieSecretId,
      MONOPOLYFUN_CSRF: csrfSecretId,
    },
    openclaw: {
      skillKey: "monopolyfun-agent",
      skillsEntry: {
        enabled: true,
        env: {
          MONOPOLYFUN_BASE_URL: baseUrl,
          // 中文注释：这里直接输出 OpenClaw 官方 SecretRef 形状，外部运行时可以原样写入 skills.entries.<skillKey>.env。
          MONOPOLYFUN_COOKIE: {
            source: secretSource,
            provider: secretProvider,
            id: cookieSecretId,
          },
          MONOPOLYFUN_CSRF: {
            source: secretSource,
            provider: secretProvider,
            id: csrfSecretId,
          },
        },
        config: {
          bootstrapAccountHandle: auth.account.handle,
        },
      },
    },
    runtime: {
      cookiePresent: auth.session.sessionCookiePresent(),
      csrfPresent: auth.session.csrfCookiePresent(),
    },
    healthcheck: {
      postTurnOk: healthcheck.postTurnOk,
      workbenchTurnOk: healthcheck.workbenchTurnOk,
    },
  });
} catch (error) {
  printJson(buildFailurePayload(error, {
    status: "blocked",
    phase: "runtime_bootstrap",
  }));
  process.exit(1);
}
