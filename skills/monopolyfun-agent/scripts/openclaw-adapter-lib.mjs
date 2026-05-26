import { appendFile, mkdir, readFile, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

export const DEFAULT_JOB_ID = "monopolyfun-autopilot";
export const DEFAULT_AGENT_ID = "main";
export const DEFAULT_EVERY = "1m";
export const MAX_BACKOFF_MS = 3 * 60 * 60 * 1000;
export const OFFICIAL_DELIVERY_CHANNELS = ["feishu"];

export function scriptPaths(metaUrl) {
  const scriptDir = dirname(fileURLToPath(metaUrl));
  const skillDir = dirname(scriptDir);
  return { scriptDir, skillDir, manifestPath: join(skillDir, "skill.manifest.json") };
}

export function resolveOpenClawHome(flags = new Map()) {
  return flags.get("openclaw-home")
    || process.env.OPENCLAW_HOME
    || join(homedir(), ".openclaw");
}

export function resolveAdapterDir(openclawHome) {
  return join(openclawHome, "monopolyfun");
}

export function resolveCredentialPaths(openclawHome) {
  const credentialsDir = join(openclawHome, "credentials");
  return {
    credentialsDir,
    handleFile: join(credentialsDir, "monopolyfun-handle.txt"),
    loginFile: join(credentialsDir, "monopolyfun-login.txt"),
    sessionCacheFile: join(resolveAdapterDir(openclawHome), "runtime-session.json"),
  };
}

export async function readJsonFile(filePath, fallback = null) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch (error) {
    if (error?.code === "ENOENT") {
      return fallback;
    }
    throw error;
  }
}

export async function readManifest(manifestPath) {
  return await readJsonFile(manifestPath, {});
}

export async function writeSecretFile(filePath, value) {
  await mkdir(dirname(filePath), { recursive: true, mode: 0o700 });
  await writeFile(filePath, `${String(value).trim()}\n`, { mode: 0o600 });
}

export function buildRuntimeEnv(input) {
  return {
    ...process.env,
    MONOPOLYFUN_BASE_URL: input.baseUrl,
    MONOPOLYFUN_HANDLE_FILE: input.handleFile,
    MONOPOLYFUN_LOGIN_FILE: input.loginFile,
    MONOPOLYFUN_SESSION_CACHE_FILE: input.sessionCacheFile,
  };
}

export async function appendJsonl(filePath, payload) {
  await mkdir(dirname(filePath), { recursive: true });
  await appendFile(filePath, `${JSON.stringify(payload)}\n`);
}

export function gatewayCliArgs(flags = new Map()) {
  const args = [];
  const url = flags.get("url") || process.env.OPENCLAW_GATEWAY_URL;
  const token = flags.get("token") || process.env.OPENCLAW_GATEWAY_TOKEN;
  if (url) {
    args.push("--url", url);
  }
  if (token) {
    args.push("--token", token);
  }
  return args;
}

export async function runCommand(argv, input = {}) {
  const result = await new Promise((resolve) => {
    const child = spawn(argv[0], argv.slice(1), {
      cwd: input.cwd,
      env: input.env ?? process.env,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    child.stdout?.on("data", (chunk) => {
      stdout += String(chunk);
    });
    child.stderr?.on("data", (chunk) => {
      stderr += String(chunk);
    });
    child.on("error", (error) => {
      resolve({ code: null, stdout, stderr: stderr || String(error), error });
    });
    child.on("close", (code) => {
      resolve({ code, stdout, stderr });
    });
  });
  if (input.allowFailure) {
    return result;
  }
  if (result.code !== 0) {
    throw new Error(result.stderr.trim() || result.stdout.trim() || `${argv[0]} exited with ${result.code}`);
  }
  return result;
}

export async function runJsonCommand(argv, input = {}) {
  const result = await runCommand(argv, input);
  const trimmed = result.stdout.trim();
  if (!trimmed) {
    return {};
  }
  return JSON.parse(trimmed);
}

export async function listCronJobs(input) {
  const openclawBin = input.openclawBin || "openclaw";
  const payload = await runJsonCommand([
    openclawBin,
    "cron",
    "list",
    "--json",
    ...gatewayCliArgs(input.flags),
  ]);
  return Array.isArray(payload.jobs) ? payload.jobs : [];
}

export function findCronJob(jobs, idOrName) {
  const needle = String(idOrName || "").trim().toLowerCase();
  return jobs.find((job) => {
    return String(job.id || "").toLowerCase() === needle
      || String(job.name || "").toLowerCase() === needle;
  });
}

export async function runOpenClawCron(input) {
  const openclawBin = input.openclawBin || "openclaw";
  const supportsJsonFlag = input.json !== false && input.args?.[0] === "add";
  return await runJsonCommand([
    openclawBin,
    "cron",
    ...input.args,
    ...(supportsJsonFlag ? ["--json"] : []),
    ...gatewayCliArgs(input.flags),
  ]);
}

export async function loadSessions(openclawHome, agentId = DEFAULT_AGENT_ID) {
  const filePath = join(openclawHome, "agents", agentId, "sessions", "sessions.json");
  const sessions = await readJsonFile(filePath, {});
  return { filePath, sessions: sessions && typeof sessions === "object" ? sessions : {} };
}

export function resolveLatestDeliverableSession(sessions, input = {}) {
  const rows = listDeliverableSessionRows(sessions, input);
  return rows[0] || null;
}

export function resolvePreferredReportSession(sessions, input = {}) {
  const rows = listDeliverableSessionRows(sessions, input);
  const officialRows = rows.filter((row) => resolveOfficialDeliveryRoute(row).kind === "official");
  // 中文注释：定时汇报优先选择官方可推送通道，网页会话继续作为本地状态镜像。
  return officialRows[0] || rows[0] || null;
}

export function listDeliverableSessionRows(sessions, input = {}) {
  const agentId = input.agentId || DEFAULT_AGENT_ID;
  return Object.entries(sessions)
    .map(([sessionKey, value]) => ({ sessionKey, value }))
    .filter(({ sessionKey, value }) => {
      if (!sessionKey.startsWith(`agent:${agentId}:`)) {
        return false;
      }
      if (!value || typeof value !== "object") {
        return false;
      }
      if (value.status === "failed" || value.archivedAt || value.deletedAt) {
        return false;
      }
      return isDeliverableSession(value);
    })
    .sort((a, b) => Number(b.value.updatedAt || 0) - Number(a.value.updatedAt || 0));
}

export function isDeliverableSession(entry) {
  const channel = normalizeString(entry.lastChannel)
    || normalizeString(entry.deliveryContext?.channel)
    || normalizeString(entry.origin?.provider)
    || normalizeString(entry.origin?.surface);
  if (!channel) {
    return false;
  }
  if (isInternalChannel(channel)) {
    return Boolean(entry.sessionId);
  }
  const to = normalizeString(entry.lastTo)
    || normalizeString(entry.deliveryContext?.to)
    || normalizeString(entry.origin?.to);
  return Boolean(to);
}

export function isInternalChannel(channel) {
  return ["webchat", "dashboard", "tui", "cli"].includes(String(channel || "").toLowerCase());
}

export function sessionDigest(row) {
  if (!row) {
    return null;
  }
  const channel = row.value?.lastChannel ?? row.value?.deliveryContext?.channel ?? row.value?.origin?.provider;
  const to = row.value?.lastTo ?? row.value?.deliveryContext?.to ?? row.value?.origin?.to;
  return {
    sessionKey: row.sessionKey,
    sessionTarget: `session:${row.sessionKey}`,
    sessionId: row.value?.sessionId,
    updatedAt: row.value?.updatedAt,
    lastChannel: channel,
    lastToPresent: Boolean(to),
    lastAccountId: row.value?.lastAccountId ?? row.value?.deliveryContext?.accountId ?? row.value?.origin?.accountId,
  };
}

export function resolveOfficialDeliveryRoute(row) {
  const entry = row?.value ?? row;
  const channel = normalizeString(entry?.lastChannel)
    || normalizeString(entry?.deliveryContext?.channel)
    || normalizeString(entry?.origin?.provider)
    || normalizeString(entry?.origin?.surface);
  const normalizedChannel = channel.toLowerCase();
  const to = normalizeString(entry?.lastTo)
    || normalizeString(entry?.deliveryContext?.to)
    || normalizeString(entry?.origin?.to);
  const accountId = normalizeString(entry?.lastAccountId)
    || normalizeString(entry?.deliveryContext?.accountId)
    || normalizeString(entry?.origin?.accountId);

  if (OFFICIAL_DELIVERY_CHANNELS.includes(normalizedChannel) && to) {
    return {
      kind: "official",
      channel: normalizedChannel,
      to,
      accountId: accountId || null,
      reason: "supported-official-route",
    };
  }

  // 中文注释：OpenClaw 2026.5.6 的官方 cron delivery 只接受外部投递通道，网页会话走本地日志镜像。
  return {
    kind: "mirror",
    channel: normalizedChannel || null,
    to: to || null,
    accountId: accountId || null,
    reason: isInternalChannel(normalizedChannel)
      ? "internal-session-route"
      : "unsupported-official-route",
  };
}

export function normalizeString(value) {
  return typeof value === "string" ? value.trim() : "";
}

export function parseDurationMs(value, fallbackMs = 60_000) {
  const input = normalizeString(value);
  const match = input.match(/^(\d+)\s*(ms|s|m|h|d)$/i);
  if (!match) {
    return fallbackMs;
  }
  const amount = Number.parseInt(match[1], 10);
  const unit = match[2].toLowerCase();
  const multiplier = unit === "ms"
    ? 1
    : unit === "s"
      ? 1_000
      : unit === "m"
        ? 60_000
        : unit === "h"
          ? 3_600_000
          : 86_400_000;
  return Math.max(1_000, amount * multiplier);
}

export function nextBackoffMs(consecutiveFailures) {
  const steps = [60_000, 5 * 60_000, 15 * 60_000, 60 * 60_000, MAX_BACKOFF_MS];
  return Math.min(steps[Math.max(0, consecutiveFailures - 1)] ?? MAX_BACKOFF_MS, MAX_BACKOFF_MS);
}
