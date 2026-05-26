#!/usr/bin/env node

import { mkdir, readFile } from "node:fs/promises";
import { createInterface } from "node:readline/promises";
import {
  DEFAULT_AGENT_ID,
  DEFAULT_EVERY,
  DEFAULT_JOB_ID,
  appendJsonl,
  buildRuntimeEnv,
  findCronJob,
  listCronJobs,
  readManifest,
  resolveAdapterDir,
  resolveCredentialPaths,
  resolveOpenClawHome,
  runCommand,
  runOpenClawCron,
  scriptPaths,
  writeSecretFile,
} from "./openclaw-adapter-lib.mjs";
import { buildFailurePayload, formatHelp, parseArgs, printJson, readOption } from "./runtime-session.mjs";

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/setup-openclaw.mjs [--base-url <url>] [--handle <handle>] [--password <password>]",
    "",
    "options:",
    "  --base-url       MonopolyFun API base url",
    "  --handle         MonopolyFun account handle",
    "  --password       MonopolyFun password",
    "  --login-file     Existing file that stores MonopolyFun password",
    "  --openclaw-home  OpenClaw home, default ~/.openclaw",
    "  --openclaw-bin   OpenClaw CLI binary, default openclaw",
    "  --agent          OpenClaw agent id, default main",
    "  --job            cron job id or name, default monopolyfun-autopilot",
    "  --url            Gateway websocket url",
    "  --token          Gateway token",
    "  --skip-run       skip immediate cron run after setup",
  ]));
  process.exit(0);
}

try {
  const paths = scriptPaths(import.meta.url);
  const manifest = await readManifest(paths.manifestPath);
  const adapter = manifest.openclawAdapter || {};
  const cronConfig = adapter.cronJob || {};
  const openclawHome = resolveOpenClawHome(flags);
  const adapterDir = resolveAdapterDir(openclawHome);
  const credentialPaths = resolveCredentialPaths(openclawHome);
  await mkdir(adapterDir, { recursive: true });

  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: manifest.defaultBaseUrl || "https://monopolyfun.app",
  });
  const handle = await resolveSetupValue("handle", {
    flags,
    envKeys: ["MONOPOLYFUN_HANDLE"],
    filePath: credentialPaths.handleFile,
    required: true,
  });
  const password = await resolveSetupValue("password", {
    flags,
    envKeys: ["MONOPOLYFUN_PASSWORD", "MONOPOLYFUN_LOGIN_SECRET", "MONOPOLYFUN_LOGIN_VALUE"],
    filePath: flags.get("login-file") || credentialPaths.loginFile,
    required: true,
    secret: true,
  });
  await writeSecretFile(credentialPaths.handleFile, handle);
  await writeSecretFile(credentialPaths.loginFile, password);

  const env = buildRuntimeEnv({
    baseUrl,
    handleFile: credentialPaths.handleFile,
    loginFile: credentialPaths.loginFile,
    sessionCacheFile: credentialPaths.sessionCacheFile,
  });

  // 中文注释：setup 先完成 MonopolyFun 账号自检，再注册 OpenClaw 官方 cron，避免创建无法工作的后台任务。
  const bootstrap = await runCommand([
    process.execPath,
    "scripts/runtime-bootstrap.mjs",
    "--base-url",
    baseUrl,
    "--handle",
    handle,
    "--login-file",
    credentialPaths.loginFile,
  ], { cwd: paths.skillDir, env });
  const healthcheck = await runCommand([
    process.execPath,
    "scripts/runtime-healthcheck.mjs",
  ], { cwd: paths.skillDir, env });
  const workbench = await runCommand([
    process.execPath,
    "scripts/runtime-turn.mjs",
    "--summary",
    "--input",
    "{\"intent\":\"view\",\"scene\":\"workbench\"}",
  ], { cwd: paths.skillDir, env });

  const openclawBin = readOption(flags, "openclaw-bin", {
    envKeys: ["OPENCLAW_BIN"],
    defaultValue: "openclaw",
  });
  const agentId = readOption(flags, "agent", { defaultValue: DEFAULT_AGENT_ID });
  const jobId = readOption(flags, "job", { defaultValue: cronConfig.id || cronConfig.name || DEFAULT_JOB_ID });
  const jobs = await listCronJobs({ openclawBin, flags });
  const existingJob = findCronJob(jobs, jobId);
  const bind = await runCommand([
    process.execPath,
    "scripts/openclaw-session-bind.mjs",
    "--job",
    jobId,
    "--agent",
    agentId,
    "--openclaw-home",
    openclawHome,
    "--openclaw-bin",
    openclawBin,
    "--dry-run",
    ...openclawPassthroughFlags(flags),
  ], { cwd: paths.skillDir, env, allowFailure: true });
  const bindPreview = parseJsonOrNull(bind.stdout);
  const selected = bindPreview?.selected;
  const route = bindPreview?.route;
  const sessionArgs = selected
    ? [
        "--session",
        selected.sessionTarget,
        "--session-key",
        selected.sessionKey,
        ...(route?.kind === "official"
          ? [
              "--announce",
              "--channel",
              route.channel,
              "--to",
              route.to,
              ...(route.accountId ? ["--account", route.accountId] : []),
              "--best-effort-deliver",
            ]
          : ["--announce", "--channel", "last", "--best-effort-deliver"]),
      ]
    : ["--session", "isolated", "--no-deliver"];
  const cronMessage = buildCronMessage({ baseUrl, credentialPaths, skillDir: paths.skillDir });
  const commonCronArgs = [
    "--agent",
    agentId,
    "--every",
    cronConfig.every || DEFAULT_EVERY,
    "--message",
    cronMessage,
    "--timeout-seconds",
    String(cronConfig.timeoutSeconds || 600),
    "--light-context",
    "--tools",
    Array.isArray(cronConfig.tools) ? cronConfig.tools.join(",") : "exec,read,write",
    ...sessionArgs,
  ];

  let cronResult;
  if (existingJob) {
    cronResult = await runOpenClawCron({
      openclawBin,
      flags,
      args: [
        "edit",
        existingJob.id,
        "--enable",
        "--name",
        cronConfig.name || DEFAULT_JOB_ID,
        ...commonCronArgs,
      ],
    });
  } else {
    cronResult = await runOpenClawCron({
      openclawBin,
      flags,
      args: [
        "add",
        "--name",
        cronConfig.name || DEFAULT_JOB_ID,
        "--description",
        `Auto configured by MonopolyFun skill adapter`,
        ...commonCronArgs,
      ],
    });
  }

  let immediateRun = null;
  if (!flags.has("skip-run")) {
    immediateRun = await runOpenClawCron({
      openclawBin,
      flags,
      args: ["run", cronResult.id || existingJob?.id || jobId],
    });
  }

  const payload = sanitizeSetupPayload({
    ok: true,
    skill: manifest.id || "monopolyfun-agent",
    openclawHome,
    baseUrl,
    credentials: {
      handleFile: credentialPaths.handleFile,
      loginFile: credentialPaths.loginFile,
      sessionCacheFile: credentialPaths.sessionCacheFile,
    },
    checks: {
      bootstrap: parseJsonOrNull(bootstrap.stdout),
      healthcheck: parseJsonOrNull(healthcheck.stdout),
      workbench: parseJsonOrNull(workbench.stdout),
    },
    sessionBinding: selected ? { status: "ready", selected } : { status: "blocked", reason: bindPreview?.reason || "no-deliverable-session" },
    cron: {
      action: existingJob ? "updated" : "created",
      result: cronResult,
      immediateRun,
    },
    checkedAt: new Date().toISOString(),
  });
  await appendJsonl(`${adapterDir}/setup.jsonl`, payload);
  printJson(payload);
} catch (error) {
  const payload = buildFailurePayload(error, {
    status: "blocked",
    phase: "setup_openclaw",
  });
  printJson(payload);
  process.exit(1);
}

function buildCronMessage(input) {
  const command = [
    `MONOPOLYFUN_BASE_URL=${shellEnvValue(input.baseUrl)}`,
    `MONOPOLYFUN_HANDLE_FILE=${shellEnvValue(input.credentialPaths.handleFile)}`,
    `MONOPOLYFUN_LOGIN_FILE=${shellEnvValue(input.credentialPaths.loginFile)}`,
    `MONOPOLYFUN_SESSION_CACHE_FILE=${shellEnvValue(input.credentialPaths.sessionCacheFile)}`,
    `node ${shellEnvValue(`${input.skillDir}/scripts/runtime-turn.mjs`)}`,
    "--summary",
    "--input",
    shellEnvValue("{\"intent\":\"view\",\"scene\":\"workbench\"}"),
  ].join(" ");
  // 中文注释：cron prompt 直接给出 skill 命令，避免 agent 把定时任务误解成本地文件巡检。
  return [
    "Use the installed monopolyfun-agent skill.",
    `Run this exact read command first: ${command}.`,
    "Report the visible MonopolyFun workbench result to the user in natural concise language.",
    "Only run write actions when the user has given an explicit mandate.",
  ].join(" ");
}

function shellEnvValue(value) {
  return `'${String(value).replaceAll("'", "'\"'\"'")}'`;
}

async function resolveSetupValue(name, input) {
  const direct = input.flags.get(name)
    || firstEnv(input.envKeys)
    || await readOptional(input.filePath);
  if (direct) {
    return direct.trim();
  }
  if (!input.required) {
    return "";
  }
  if (!process.stdin.isTTY) {
    throw new Error(`missing required setup value: ${name}`);
  }
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  try {
    const answer = await rl.question(`${name}: `);
    if (!answer.trim()) {
      throw new Error(`missing required setup value: ${name}`);
    }
    return answer.trim();
  } finally {
    rl.close();
  }
}

async function readOptional(filePath) {
  if (!filePath) {
    return "";
  }
  try {
    return (await readFile(filePath, "utf8")).trim();
  } catch {
    return "";
  }
}

function firstEnv(keys = []) {
  for (const key of keys) {
    const value = process.env[key];
    if (value?.trim()) {
      return value.trim();
    }
  }
  return "";
}

function parseJsonOrNull(value) {
  try {
    return JSON.parse(String(value || "").trim());
  } catch {
    return null;
  }
}

function sanitizeSetupPayload(payload) {
  // 中文注释：setup 证据只保留能力状态，登录 secret、cookie、csrf 必须在落盘和输出前脱敏。
  const copy = JSON.parse(JSON.stringify(payload));
  const secrets = copy?.checks?.bootstrap?.secrets;
  if (secrets && typeof secrets === "object") {
    for (const key of Object.keys(secrets)) {
      secrets[key] = "[redacted]";
    }
  }
  const runtime = copy?.checks?.bootstrap?.runtime;
  if (runtime && typeof runtime === "object") {
    runtime.cookiePresent = Boolean(runtime.cookiePresent);
    runtime.csrfPresent = Boolean(runtime.csrfPresent);
  }
  return copy;
}

function openclawPassthroughFlags(inputFlags) {
  const args = [];
  for (const name of ["url", "token"]) {
    const value = inputFlags.get(name);
    if (value) {
      args.push(`--${name}`, value);
    }
  }
  return args;
}
