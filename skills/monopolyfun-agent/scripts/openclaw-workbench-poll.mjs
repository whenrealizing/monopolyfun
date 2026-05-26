#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import {
  DEFAULT_AGENT_ID,
  DEFAULT_JOB_ID,
  appendJsonl,
  buildRuntimeEnv,
  loadSessions,
  nextBackoffMs,
  resolveAdapterDir,
  resolveCredentialPaths,
  resolveLatestDeliverableSession,
  resolveOpenClawHome,
  runCommand,
  runOpenClawCron,
  scriptPaths,
  sessionDigest,
} from "./openclaw-adapter-lib.mjs";
import { buildFailurePayload, formatHelp, parseArgs, printJson, readOption } from "./runtime-session.mjs";

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/openclaw-workbench-poll.mjs [--respect-backoff] [--job monopolyfun-autopilot]",
    "",
    "options:",
    "  --base-url         MonopolyFun API base url",
    "  --openclaw-home    OpenClaw home, default ~/.openclaw",
    "  --openclaw-bin     OpenClaw CLI binary, default openclaw",
    "  --agent            OpenClaw agent id, default main",
    "  --job              cron job id or name, default monopolyfun-autopilot",
    "  --respect-backoff  skip poll while adapter backoff is active",
    "  --run-cron-on-action trigger OpenClaw cron run when low-risk actions exist",
    "  --url              Gateway websocket url",
    "  --token            Gateway token",
  ]));
  process.exit(0);
}

try {
  const { skillDir } = scriptPaths(import.meta.url);
  const openclawHome = resolveOpenClawHome(flags);
  const adapterDir = resolveAdapterDir(openclawHome);
  const paths = resolveCredentialPaths(openclawHome);
  const stateFile = `${adapterDir}/backoff-state.json`;
  const agentId = readOption(flags, "agent", { defaultValue: DEFAULT_AGENT_ID });
  const jobId = readOption(flags, "job", { defaultValue: DEFAULT_JOB_ID });
  const openclawBin = readOption(flags, "openclaw-bin", {
    envKeys: ["OPENCLAW_BIN"],
    defaultValue: "openclaw",
  });
  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: "https://monopolyfun.app",
  });

  const sessions = await loadSessions(openclawHome, agentId);
  const latest = resolveLatestDeliverableSession(sessions.sessions, { agentId });
  const latestSession = sessionDigest(latest);
  let state = await readBackoffState(stateFile);
  const now = Date.now();
  const sessionMoved = latestSession?.updatedAt && latestSession.updatedAt > Number(state.backoffStartedAtMs || 0);
  if (sessionMoved && state.consecutiveFailures > 0) {
    // 中文注释：用户回复或通道有新动作时，适配层立即恢复正常轮询频率。
    state = clearBackoffState(state, "session_activity");
    await writeBackoffState(stateFile, state);
  }

  if (flags.has("respect-backoff") && state.backoffUntilMs && state.backoffUntilMs > now) {
    const payload = {
      status: "skipped",
      reason: "adapter-backoff",
      backoffUntil: new Date(state.backoffUntilMs).toISOString(),
      latestSession,
      checkedAt: new Date().toISOString(),
    };
    await appendJsonl(`${adapterDir}/workbench-poll.jsonl`, payload);
    printJson(payload);
    process.exit(0);
  }

  const env = buildRuntimeEnv({
    baseUrl,
    handleFile: paths.handleFile,
    loginFile: paths.loginFile,
    sessionCacheFile: paths.sessionCacheFile,
  });
  const turn = await runCommand([
    process.execPath,
    "scripts/runtime-turn.mjs",
    "--summary",
    "--input",
    "{\"intent\":\"view\",\"scene\":\"workbench\"}",
  ], { cwd: skillDir, env });
  const summary = JSON.parse(turn.stdout.trim());
  const hasAction = Array.isArray(summary?.autopilot?.lowRiskActionIds)
    && summary.autopilot.lowRiskActionIds.length > 0;

  state = clearBackoffState(state, hasAction ? "workbench_action" : "poll_success");
  await writeBackoffState(stateFile, state);

  let cronRun = null;
  if (hasAction && flags.has("run-cron-on-action")) {
    cronRun = await runOpenClawCron({
      openclawBin,
      flags,
      args: ["run", jobId],
    });
  }

  const payload = {
    status: "ok",
    baseUrl,
    latestSession,
    workbench: summary,
    cronRun,
    checkedAt: new Date().toISOString(),
  };
  await appendJsonl(`${adapterDir}/workbench-poll.jsonl`, payload);
  printJson(payload);
} catch (error) {
  const openclawHome = resolveOpenClawHome(flags);
  const adapterDir = resolveAdapterDir(openclawHome);
  const stateFile = `${adapterDir}/backoff-state.json`;
  const previous = await readBackoffState(stateFile);
  const consecutiveFailures = Number(previous.consecutiveFailures || 0) + 1;
  const delayMs = nextBackoffMs(consecutiveFailures);
  const next = {
    consecutiveFailures,
    backoffStartedAtMs: previous.backoffStartedAtMs || Date.now(),
    backoffUntilMs: Date.now() + delayMs,
    lastFailureAtMs: Date.now(),
    lastError: error instanceof Error ? error.message : String(error),
    maxBackoffMs: 3 * 60 * 60 * 1000,
  };
  await writeBackoffState(stateFile, next);
  const payload = {
    ...buildFailurePayload(error, {
      status: "blocked",
      phase: "openclaw_workbench_poll",
    }),
    backoff: {
      consecutiveFailures,
      delayMs,
      until: new Date(next.backoffUntilMs).toISOString(),
      maxHours: 3,
    },
  };
  await appendJsonl(`${adapterDir}/workbench-poll.jsonl`, payload);
  printJson(payload);
  process.exit(1);
}

async function readBackoffState(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch {
    return {};
  }
}

async function writeBackoffState(filePath, state) {
  await mkdir(dirname(filePath), { recursive: true });
  await writeFile(filePath, `${JSON.stringify(state, null, 2)}\n`, { mode: 0o600 });
}

function clearBackoffState(previous, reason) {
  return {
    ...previous,
    consecutiveFailures: 0,
    backoffUntilMs: 0,
    lastRecoveryReason: reason,
    lastRecoveryAtMs: Date.now(),
  };
}
